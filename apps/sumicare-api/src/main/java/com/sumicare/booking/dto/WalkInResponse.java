package com.sumicare.booking.dto;

import java.util.UUID;

public record WalkInResponse(
        UUID slipId,
        UUID bookingId,
        UUID sessionId,
        String tsn
) {}
