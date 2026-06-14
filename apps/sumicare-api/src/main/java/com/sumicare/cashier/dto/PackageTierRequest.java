package com.sumicare.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PackageTierRequest(
        @NotNull Long serviceId,
        @NotNull @DecimalMin("0.00") BigDecimal weekdayPrice,
        @NotNull @DecimalMin("0.00") BigDecimal weekendPrice
) {}
