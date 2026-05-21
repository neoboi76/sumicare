package com.sumicare.cashier.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        BigDecimal amount,
        @Size(max = 40) String reason,
        @Size(max = 255) String notes
) {}
