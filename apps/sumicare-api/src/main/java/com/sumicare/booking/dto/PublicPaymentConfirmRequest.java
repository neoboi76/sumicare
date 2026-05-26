package com.sumicare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PublicPaymentConfirmRequest(
        @NotNull UUID orderId,
        @NotBlank String intentId,
        String paymentMethod
) {}
