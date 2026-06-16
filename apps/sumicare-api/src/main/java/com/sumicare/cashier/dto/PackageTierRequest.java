/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PackageTierRequest(
        @NotNull Long serviceId,
        @NotNull @DecimalMin("0.00") BigDecimal weekdayPrice,
        @NotNull @DecimalMin("0.00") BigDecimal weekendPrice
) {}
