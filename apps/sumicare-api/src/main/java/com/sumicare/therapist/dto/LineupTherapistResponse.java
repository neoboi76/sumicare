package com.sumicare.therapist.dto;

import java.util.UUID;

public record LineupTherapistResponse(
        UUID therapistId,
        String nickname,
        String gender,
        String shiftLabel,
        String flag,
        boolean skipped,
        int position
) {}
