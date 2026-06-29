/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderItemRequest(
        Long packageId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String roomType,
        Integer position,
        List<CreateOrderItemAttendeeRequest> attendees
) {}
