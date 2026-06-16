/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.dto;

import com.sumicare.cashier.dto.PaymentDetailsRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        UUID clientId,
        @NotBlank String clientNickname,
        String clientEmail,
        String lockerNumber,
        @NotNull Long serviceId,
        @NotBlank String reservationType,
        @NotNull OffsetDateTime scheduledAt,
        Integer pax,
        String clientGender,
        Long packageId,
        Long packageTierId,
        String nationality,
        String roomType,
        String paymentMethod,
        PaymentDetailsRequest paymentDetails,
        List<PublicAttendeeRequest> attendees,
        List<CreateBookingItemRequest> items,
        String voucherCode,
        @Size(max = 1000) String remarks,
        @Size(max = 120) String preferredTherapist
) {}
