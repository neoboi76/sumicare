package com.sumicare.cashier.dto;

import java.util.UUID;

public record OrderItemAttendeeResponse(
        UUID id,
        Long serviceId,
        String serviceName,
        Long packageTierId,
        String lockerNumber,
        String clientGender,
        UUID sessionId,
        UUID treatmentSlipId,
        int position
) {}
