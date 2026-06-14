package com.sumicare.cashier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayMongoConfirmRequest(
        @NotBlank String intentId,
        BigDecimal amount,
        @Size(max = 20) String paymentMethod
) {}
