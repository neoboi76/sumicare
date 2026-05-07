package com.sumicare.therapist.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTherapistRequest(
        String staffNumber,
        @NotBlank String nickname,
        @NotBlank String gender,
        boolean backup,
        Long shiftId
) {}
