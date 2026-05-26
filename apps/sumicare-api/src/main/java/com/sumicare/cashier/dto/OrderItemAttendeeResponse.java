package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemAttendeeResponse(
        UUID id,
        Long serviceId,
        String serviceName,
        Long packageTierId,
        String lockerNumber,
        String clientGender,
        UUID sessionId,
        String sessionStatus,
        boolean sessionExtended,
        UUID treatmentSlipId,
        int position,
        BigDecimal discount,
        String providedTsn
) {}
