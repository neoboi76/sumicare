/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        BigDecimal amount,
        @Size(max = 40) String reason,
        @Size(max = 255) String notes
) {}
