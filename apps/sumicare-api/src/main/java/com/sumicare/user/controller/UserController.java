package com.sumicare.user.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.user.dto.CreateUserRequest;
import com.sumicare.user.dto.UpdateUserRequest;
import com.sumicare.user.dto.UserResponse;
import com.sumicare.user.service.UserService;
import jakarta.validation.Valid;
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
    public UserResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(UUID.fromString(principal.organizationId()), request);
    }

    @PatchMapping("/{userId}")
    public UserResponse update(@PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
        return userService.updateUser(userId, request);
    }
}
