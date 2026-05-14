package com.sumicare.cashier.dto;

public record CreateOrderItemAttendeeRequest(
        Long serviceId,
        Long packageTierId,
        String lockerNumber,
        String clientGender,
        Integer position
) {}
