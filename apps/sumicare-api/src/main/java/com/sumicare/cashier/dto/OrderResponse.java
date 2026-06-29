/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID bookingId,
        String bookingReference,
        UUID treatmentSlipId,
        UUID cashierUserId,
        String cashierDisplayName,
        String clientNickname,
        UUID clientId,
        String clientEmail,
        String serviceName,
        String orNumber,
        String referenceNumber,
        String notes,
        String preferredTherapist,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal extensionAmount,
        int extensionMinutes,
        BigDecimal amountPaid,
        BigDecimal balance,
        String status,
        OffsetDateTime scheduledAt,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime cancelledAt,
        String cancelledReason,
        String transactorName,
        boolean groupBooking,
        boolean weekend,
        String roomType,
        BigDecimal roomTypeCharge,
        boolean sessionCompleted,
        boolean couplePackage,
        String lastEditedByDisplayName,
        List<OrderItemResponse> items
) {}
