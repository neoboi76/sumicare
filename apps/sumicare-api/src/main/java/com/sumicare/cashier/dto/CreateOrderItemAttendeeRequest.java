package com.sumicare.cashier.dto;

import java.math.BigDecimal;

public record CreateOrderItemAttendeeRequest(
        Long serviceId,
        Long packageTierId,
        String lockerNumber,
        String clientGender,
        Integer position,
        BigDecimal discount,
        String providedTsn
) {}
