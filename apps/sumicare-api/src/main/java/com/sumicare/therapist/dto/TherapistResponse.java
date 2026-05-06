package com.sumicare.therapist.dto;

import java.util.UUID;

public record TherapistResponse(
        UUID id,
        String staffNumber,
        String nickname,
        String gender,
        boolean backup,
        boolean active
) {}
