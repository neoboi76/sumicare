/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        Long packageId,
        String packageName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String roomType,
        BigDecimal roomTypeCharge,
        int position,
        List<OrderItemAttendeeResponse> attendees
) {}
