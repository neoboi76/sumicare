package com.sumicare.cashier.dto;

import java.math.BigDecimal;

public record PackageTierResponse(
        Long id,
        Long serviceId,
        String serviceCode,
        String serviceName,
        BigDecimal weekdayPrice,
        BigDecimal weekendPrice
) {}
