/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.dto;

public record PublicPaymentResponse(
        String status,
        String intentId,
        String redirectUrl,
        String orNumber,
        String reference,
        String clientNickname,
        String packageName,
        String serviceName,
        String scheduledAt,
        String reservationType) {}
