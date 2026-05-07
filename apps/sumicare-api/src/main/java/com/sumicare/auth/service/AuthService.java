package com.sumicare.auth.service;

import com.sumicare.auth.dto.LoginRequest;
import com.sumicare.auth.dto.TokenResponse;
import com.sumicare.common.config.AppProperties;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

    public static final String REFRESH_COOKIE = "sumicare_refresh";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final LoginRateLimiter rateLimiter;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties appProperties, LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.rateLimiter = rateLimiter;
    }

    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String key = httpRequest.getRemoteAddr() + ":" + request.username();
        if (!rateLimiter.tryConsume(key)) {
            throw new LockedException("Too many login attempts");
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!user.isActive()) throw new AccessDeniedException("User is deactivated");
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isEmailVerified()) {
            throw new AccessDeniedException("Email address not verified. Please check your inbox for the verification link.");
        }
        String access = jwtService.issueAccessToken(user.getId(), user.getOrganizationId(), user.getRole().getCode());
        String refresh = jwtService.issueRefreshToken(user.getId(), user.getOrganizationId());
        writeRefreshCookie(response, refresh);
        return new TokenResponse(access, "Bearer", appProperties.jwt().accessExpiryMs() / 1000L, user.getRole().getCode());
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
