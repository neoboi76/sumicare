package com.sumicare.booking.dto;

import java.util.UUID;

public record StartSessionRequest(
        UUID primaryTherapistId,
        UUID secondaryTherapistId,
        UUID roomId,
        UUID bedId,
        boolean specificallyRequested
) {}
