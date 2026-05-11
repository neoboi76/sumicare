package com.sumicare.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotBlank String paymentMethod,
        @DecimalMin("0.00") @NotNull BigDecimal amount,
        @Size(max = 100) String referenceNumber
) {}
