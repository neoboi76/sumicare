/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PendingInitiateRequest(
        @Valid @NotNull CreateOrderRequest order,
        @NotNull String paymentMethod,
        @NotNull BigDecimal amount,
        PaymentDetailsRequest paymentDetails,
        String returnPath
) {}
