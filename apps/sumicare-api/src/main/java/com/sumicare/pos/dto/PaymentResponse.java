package com.sumicare.pos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID transactionId,
        String receiptNumber,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String paymentMethod,
        OffsetDateTime processedAt
) {}
