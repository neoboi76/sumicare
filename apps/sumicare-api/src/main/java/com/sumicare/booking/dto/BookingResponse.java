package com.sumicare.booking.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String clientNickname,
        String clientEmail,
        String lockerNumber,
        Long serviceId,
        String reservationType,
        OffsetDateTime scheduledAt,
        OffsetDateTime effectiveStartAt,
        OffsetDateTime projectedEndAt,
        String status,
        UUID orderId
) {}
