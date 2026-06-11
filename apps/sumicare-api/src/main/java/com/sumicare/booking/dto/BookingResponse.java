package com.sumicare.booking.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String reference,
        String clientNickname,
        String clientEmail,
        String lockerNumber,
        Long serviceId,
        String reservationType,
        OffsetDateTime scheduledAt,
        OffsetDateTime projectedEndAt,
        String status,
        UUID orderId,
        String nationality,
        String remarks
) {}
