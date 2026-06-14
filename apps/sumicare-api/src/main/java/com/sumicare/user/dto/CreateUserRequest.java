package com.sumicare.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        String email,
        @Size(min = 8, max = 128) String password,
        @NotBlank String role,
        String displayName
) {}
