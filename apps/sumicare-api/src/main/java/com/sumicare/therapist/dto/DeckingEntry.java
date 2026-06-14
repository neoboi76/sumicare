package com.sumicare.therapist.dto;

import java.util.UUID;

public record DeckingEntry(
        UUID therapistId,
        double position,
        String flag,
        boolean skipped
) {}
