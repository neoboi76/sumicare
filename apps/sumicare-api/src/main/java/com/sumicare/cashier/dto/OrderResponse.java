package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID bookingId,
        UUID treatmentSlipId,
        UUID cashierUserId,
        String clientNickname,
        UUID clientId,
        String serviceName,
        String orNumber,
        String referenceNumber,
        String notes,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balance,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime cancelledAt,
        String cancelledReason
) {}
