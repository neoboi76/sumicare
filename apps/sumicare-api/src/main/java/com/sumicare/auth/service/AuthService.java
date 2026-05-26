package com.sumicare.auth.service;

import com.sumicare.audit.service.AuditService;
import com.sumicare.auth.dto.LoginRequest;
import com.sumicare.auth.dto.LoginResponse;
import com.sumicare.auth.dto.TokenResponse;
import com.sumicare.common.config.AppProperties;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

    public static final String REFRESH_COOKIE = "sumicare_refresh";
    private static final String SUPERADMIN = "SUPERADMIN";
    private static final int MAX_FAILED_ATTEMPTS = 2;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final LoginRateLimiter rateLimiter;
    private final AuthenticationManager authenticationManager;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties appProperties, LoginRateLimiter rateLimiter,
                       AuthenticationManager authenticationManager, MfaService mfaService,
                       EmailService emailService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.rateLimiter = rateLimiter;
        this.authenticationManager = authenticationManager;
        this.mfaService = mfaService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String key = httpRequest.getRemoteAddr() + ":" + request.username();
        if (!rateLimiter.tryConsume(key)) {
            throw new LockedException("Too many login attempts");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException ex) {
            throw new AccessDeniedException("User is deactivated");
        } catch (LockedException ex) {
            throw new LockedException("Your account is locked after too many failed sign-in attempts. Please contact an administrator.");
        } catch (BadCredentialsException ex) {
            registerFailedAttempt(request.username());
            throw new BadCredentialsException("Invalid credentials");
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isEmailVerified()) {
            throw new AccessDeniedException("Email address not verified. Please check your inbox for the verification link.");
        }
        resetFailedAttempts(user);
        if (SUPERADMIN.equalsIgnoreCase(user.getRole().getCode())) {
            return LoginResponse.authenticated(completeLogin(user, response, clientIp(httpRequest)));
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new AccessDeniedException("No email address is on file for verification. Please contact an administrator.");
        }
        MfaService.Challenge challenge = mfaService.create(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), displayNameOf(user), challenge.code());
        return LoginResponse.mfaChallenge(challenge.challengeId(), maskEmail(user.getEmail()));
    }

    public TokenResponse verifyMfa(String challengeId, String code, HttpServletRequest httpRequest, HttpServletResponse response) {
        UUID userId = mfaService.verify(challengeId, code);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return completeLogin(user, response, clientIp(httpRequest));
    }

    public void resendMfa(String challengeId) {
        MfaService.Challenge challenge = mfaService.resend(challengeId);
        userRepository.findById(challenge.userId()).ifPresent(user -> {
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailService.sendMfaCodeEmail(user.getEmail(), displayNameOf(user), challenge.code());
            }
        });
    }

    private TokenResponse completeLogin(User user, HttpServletResponse response, String ip) {
        String access = jwtService.issueAccessToken(user.getId(), user.getOrganizationId(), user.getRole().getCode());
        String refresh = jwtService.issueRefreshToken(user.getId(), user.getOrganizationId());
        writeRefreshCookie(response, refresh);
        auditService.record(user.getOrganizationId(), user.getId(), user.getRole().getCode(),
                "USER.LOGIN", "USER", user.getId().toString(), null, ip);
        return new TokenResponse(access, "Bearer", appProperties.jwt().accessExpiryMs() / 1000L, user.getRole().getCode());
    }

    private void registerFailedAttempt(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts > MAX_FAILED_ATTEMPTS) {
                user.setAccountLocked(true);
            }
            userRepository.save(user);
        });
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.isAccountLocked()) {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            userRepository.save(user);
        }
    }

    private String displayNameOf(User user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        if (refreshToken == null) throw new BadCredentialsException("Missing refresh token");
        Claims claims = jwtService.parse(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BadCredentialsException("Invalid token type");
        }
        if (jwtService.isRevoked(claims.getId())) {
            throw new BadCredentialsException("Refresh token revoked");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId).orElseThrow(() -> new BadCredentialsException("Unknown user"));
        long ttl = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 0L);
        jwtService.revoke(claims.getId(), Duration.ofMillis(ttl));
        String access = jwtService.issueAccessToken(user.getId(), user.getOrganizationId(), user.getRole().getCode());
        String newRefresh = jwtService.issueRefreshToken(user.getId(), user.getOrganizationId());
        writeRefreshCookie(response, newRefresh);
        return new TokenResponse(access, "Bearer", appProperties.jwt().accessExpiryMs() / 1000L, user.getRole().getCode());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(authHeader.substring(7));
                long ttl = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 0L);
                jwtService.revoke(claims.getId(), Duration.ofMillis(ttl));
            } catch (Exception ignored) {
            }
        }
        String refreshToken = readRefreshCookie(request);
        if (refreshToken != null) {
            try {
                Claims claims = jwtService.parse(refreshToken);
                long ttl = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 0L);
                jwtService.revoke(claims.getId(), Duration.ofMillis(ttl));
            } catch (Exception ignored) {
            }
        }
        Cookie clear = new Cookie(REFRESH_COOKIE, "");
        clear.setHttpOnly(true);
        clear.setPath("/");
        clear.setMaxAge(0);
        response.addCookie(clear);
    }

    private void writeRefreshCookie(HttpServletResponse response, String refresh) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refresh);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (appProperties.jwt().refreshExpiryMs() / 1000L));
        boolean isWildcardCors = "*".equals(appProperties.cors().allowedOrigins());
        cookie.setSecure(!isWildcardCors);
        response.addCookie(cookie);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (REFRESH_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
