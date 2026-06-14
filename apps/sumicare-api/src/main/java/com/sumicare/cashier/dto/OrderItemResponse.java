package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        Long packageId,
        String packageName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String roomType,
        BigDecimal roomTypeCharge,
        int position,
        List<OrderItemAttendeeResponse> attendees
) {}
