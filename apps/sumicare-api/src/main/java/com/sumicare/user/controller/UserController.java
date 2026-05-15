package com.sumicare.user.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.user.dto.CreateUserRequest;
import com.sumicare.user.dto.UpdateProfileRequest;
import com.sumicare.user.dto.UpdateUserRequest;
import com.sumicare.user.dto.UserResponse;
import com.sumicare.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return userService.listForOrganization(UUID.fromString(principal.organizationId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public UserResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(UUID.fromString(principal.organizationId()), request);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public UserResponse update(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        return userService.updateUser(userId, request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return userService.getById(UUID.fromString(principal.userId()));
    }

    @PatchMapping("/me/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/me/request-password-reset")
    public void requestPasswordReset(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        userService.requestPasswordReset(UUID.fromString(principal.userId()));
    }
}
