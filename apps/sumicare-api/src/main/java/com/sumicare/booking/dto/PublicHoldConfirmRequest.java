/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record PublicHoldConfirmRequest(
        @NotBlank String token,
        String intentId,
        String paymentMethod
) {}
