package com.sumicare.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CancellationDetailsResponse(
        String reference,
        String clientNickname,
        OffsetDateTime scheduledAt,
        String reservationType,
        String summary,
        String roomType,
        BigDecimal total,
        boolean paid,
        String remarks
) {}
