package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderItemRequest(
        Long packageId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String roomType,
        Integer position,
        List<CreateOrderItemAttendeeRequest> attendees
) {}
