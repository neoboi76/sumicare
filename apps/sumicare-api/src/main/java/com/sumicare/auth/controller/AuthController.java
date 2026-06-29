/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.auth.controller;

import com.sumicare.auth.dto.ContactAdminResetRequest;
import com.sumicare.auth.dto.LoginRequest;
import com.sumicare.auth.dto.LoginResponse;
import com.sumicare.auth.dto.MfaResendRequest;
import com.sumicare.auth.dto.MfaVerifyRequest;
import com.sumicare.auth.dto.RedeemInvitationRequest;
import com.sumicare.auth.dto.ResetPasswordRequest;
import com.sumicare.auth.dto.TokenResponse;
import com.sumicare.auth.service.AuthService;
import com.sumicare.auth.service.EmailService;
import com.sumicare.contact.domain.ContactMessage;
import com.sumicare.contact.repository.ContactMessageRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.user.domain.User;
import com.sumicare.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final ContactMessageRepository contactMessageRepository;
    private final OrganizationRepository organizationRepository;
    private final EmailService emailService;

    public AuthController(AuthService authService,
                          UserService userService,
                          ContactMessageRepository contactMessageRepository,
                          OrganizationRepository organizationRepository,
                          EmailService emailService) {
        this.authService = authService;
        this.userService = userService;
        this.contactMessageRepository = contactMessageRepository;
        this.organizationRepository = organizationRepository;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse response) {
        return authService.login(request, httpRequest, response);
    }

    @PostMapping("/mfa/verify")
    public TokenResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse response) {
        return authService.verifyMfa(request.challengeId(), request.code(), httpRequest, response);
    }

    @PostMapping("/mfa/resend")
    public void resendMfa(@Valid @RequestBody MfaResendRequest request) {
        authService.resendMfa(request.challengeId());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(request, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.consumePasswordReset(request.token(), request.newPassword());
    }

    @PostMapping("/contact-admin-reset")
    public ResponseEntity<Map<String, String>> contactAdminReset(@Valid @RequestBody ContactAdminResetRequest request,
                                                                  HttpServletRequest httpRequest) {
        UUID organizationId = organizationRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No organization configured"))
                .getId();
        ContactMessage msg = new ContactMessage();
        msg.setOrganizationId(organizationId);
        msg.setName(request.name() == null ? "" : request.name().trim());
        msg.setEmail(request.email() == null ? "" : request.email().trim());
        String body = (request.message() == null || request.message().isBlank())
                ? "[PASSWORD_RESET_REQUEST] Please send me a password reset link."
                : "[PASSWORD_RESET_REQUEST] " + request.message().trim();
        msg.setMessage(body);
        msg.setIpAddress(clientIp(httpRequest));
        contactMessageRepository.save(msg);

        for (User admin : userService.listOrgAdmins(organizationId)) {
            if (admin.getEmail() == null || admin.getEmail().isBlank()) continue;
            String adminName = admin.getDisplayName() != null && !admin.getDisplayName().isBlank()
                    ? admin.getDisplayName() : admin.getUsername();
            try {
                emailService.sendAdminPasswordResetNotice(admin.getEmail(), adminName,
                        request.name(), request.email(), request.message());
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of("message",
                "Your request has been sent. An administrator will contact you with a reset link shortly."));
    }

    @PostMapping("/invitations/redeem")
    public void redeemInvitation(@Valid @RequestBody RedeemInvitationRequest request) {
        userService.redeemInvitation(request.token(), request.password());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
