package com.sumicare.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentRequest(
        @NotNull UUID sessionId,
        @NotNull BigDecimal subtotal,
        BigDecimal discount,
        String voucherCode,
        @NotBlank String paymentMethod
) {}
