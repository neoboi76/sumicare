/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CancellationDetailsResponse(
        String reference,
        String clientNickname,
        OffsetDateTime scheduledAt,
        String reservationType,
        String summary,
        String roomType,
        BigDecimal total,
        boolean paid,
        String remarks
) {}
