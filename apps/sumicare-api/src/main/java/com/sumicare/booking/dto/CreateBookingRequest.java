package com.sumicare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBookingRequest(
        UUID clientId,
        @NotBlank String clientNickname,
        String lockerNumber,
        @NotNull Long serviceId,
        @NotBlank String reservationType,
        @NotNull OffsetDateTime scheduledAt
) {}
