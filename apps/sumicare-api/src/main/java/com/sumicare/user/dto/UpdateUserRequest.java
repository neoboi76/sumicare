package com.sumicare.user.dto;

public record UpdateUserRequest(
        String email,
        String password,
        String role,
        String displayName,
        Boolean active
) {}
