package com.sumicare.booking.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID bookingId,
        UUID primaryTherapistId,
        UUID secondaryTherapistId,
        UUID roomId,
        UUID bedId,
        boolean specificallyRequested,
        boolean extension,
        int extensionMinutes,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String status
) {}
