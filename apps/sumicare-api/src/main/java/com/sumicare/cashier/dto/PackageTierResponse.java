/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import java.math.BigDecimal;

public record PackageTierResponse(
        Long id,
        Long serviceId,
        String serviceCode,
        String serviceName,
        BigDecimal weekdayPrice,
        BigDecimal weekendPrice,
        Integer serviceDurationMinutes
) {}
