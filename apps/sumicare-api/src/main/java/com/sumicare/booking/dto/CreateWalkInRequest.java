package com.sumicare.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateWalkInRequest(
        @NotBlank String clientNickname,
        @NotNull Long serviceId,
        String reservationType,
        Integer pax,
        String clientGender,
        String lockerNumber,
        @NotNull OffsetDateTime startTime,
        OffsetDateTime endTime,
        UUID primaryTherapistId,
        UUID secondaryTherapistId,
        UUID roomId,
        List<UUID> bedIds,
        boolean specificallyRequested,
        Integer jacuzziMinutes,
        Integer massageMinutes,
        Boolean wineIncluded,
        String orNumber,
        String addOnOrNumber,
        String othersAddOn,
        String remarks,
        BigDecimal totalAmount,
        boolean waiverAccepted
) {}
