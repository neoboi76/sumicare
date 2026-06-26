/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

public record PendingPaymentResult(
        String status,
        String token,
        String intentId,
        String redirectUrl,
        String orderId,
        String orNumber,
        String reference,
        String nickname,
        String scheduledAt,
        String reservationType
) {}
