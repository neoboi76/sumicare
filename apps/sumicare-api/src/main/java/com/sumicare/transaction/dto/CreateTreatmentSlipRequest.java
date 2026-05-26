package com.sumicare.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTreatmentSlipRequest(
        @NotBlank @Size(max = 120) String clientNickname,
        boolean vip,
        Long serviceId,
        @Size(max = 16) String lockerNumber,
        @Size(max = 1) String clientGender,
        @Size(max = 64) String roomNumber,
        @Size(max = 64) String providedTsn
) {}
