package com.sumicare.cashier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DiscountTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 16) String amountType,
        BigDecimal percent,
        BigDecimal fixedAmount
) {}
