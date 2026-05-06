package com.sumicare.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID organizationId,
        String username,
        String email,
        String role,
        String displayName,
        boolean active,
        OffsetDateTime createdAt
) {}
