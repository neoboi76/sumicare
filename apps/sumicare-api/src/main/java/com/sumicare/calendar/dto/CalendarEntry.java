/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.calendar.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarEntry(
        UUID bookingId,
        String reference,
        String clientNickname,
        String reservationType,
        String schedulingStatus,
        OffsetDateTime scheduledAt
) {}
