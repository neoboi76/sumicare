package com.sumicare.auth.controller;

import com.sumicare.auth.dto.ForgotPasswordRequest;
import com.sumicare.auth.dto.LoginRequest;
import com.sumicare.auth.dto.RedeemInvitationRequest;
import com.sumicare.auth.dto.ResetPasswordRequest;
import com.sumicare.auth.dto.TokenResponse;
import com.sumicare.auth.service.AuthService;
import com.sumicare.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse response) {
        return authService.login(request, httpRequest, response);
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
    public void resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.consumePasswordReset(request.token(), request.newPassword());
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.requestPublicPasswordReset(request.email());
        return ResponseEntity.ok(Map.of("message", "If your email exists, a reset link has been sent."));
    }

    @PostMapping("/invitations/redeem")
    public void redeemInvitation(@Valid @RequestBody RedeemInvitationRequest request) {
        userService.redeemInvitation(request.token(), request.password());
    }
}
