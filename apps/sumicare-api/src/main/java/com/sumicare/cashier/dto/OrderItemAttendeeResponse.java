/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemAttendeeResponse(
        UUID id,
        Long serviceId,
        String serviceName,
        Long packageTierId,
        String lockerNumber,
        String clientGender,
        UUID sessionId,
        String sessionStatus,
        boolean sessionExtended,
        UUID treatmentSlipId,
        int position,
        BigDecimal discount,
        String providedTsn,
        String preferredTherapist,
        UUID primaryTherapistId,
        String therapistNickname
) {}
