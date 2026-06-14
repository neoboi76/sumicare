package com.sumicare.booking.dto;

import jakarta.validation.constraints.Size;

public record PublicAttendeeRequest(
        Long packageTierId,
        @Size(max = 16) String lockerNumber,
        @Size(max = 1) String clientGender
) {}
