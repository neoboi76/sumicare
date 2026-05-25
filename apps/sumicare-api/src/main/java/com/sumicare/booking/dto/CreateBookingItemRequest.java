package com.sumicare.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingItemRequest(
        @NotNull Long packageId,
        Long packageTierId,
        String roomType,
        List<PublicAttendeeRequest> attendees
) {}
