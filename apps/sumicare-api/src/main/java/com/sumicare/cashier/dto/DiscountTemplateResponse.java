package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DiscountTemplateResponse(
        UUID id,
        String name,
        String amountType,
        BigDecimal percent,
        BigDecimal fixedAmount
) {}
