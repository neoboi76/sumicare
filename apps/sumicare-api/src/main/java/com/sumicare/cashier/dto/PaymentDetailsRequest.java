/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import jakarta.validation.constraints.Size;

public record PaymentDetailsRequest(
        @Size(max = 32) String cardNumber,
        @Size(max = 2) String expMonth,
        @Size(max = 4) String expYear,
        @Size(max = 4) String cvc,
        @Size(max = 120) String cardHolder,
        @Size(max = 120) String cardEmail,
        @Size(max = 120) String gcashName,
        @Size(max = 20) String gcashPhone,
        @Size(max = 120) String gcashEmail
) {}
