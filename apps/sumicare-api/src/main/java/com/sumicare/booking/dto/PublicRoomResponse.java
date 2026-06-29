package com.sumicare.booking.dto;

import java.util.List;
import java.util.UUID;

public record PublicRoomResponse(
        UUID id,
        String roomNumber,
        Integer floor,
        String roomType,
        boolean rowSegmented,
        List<PublicBedResponse> beds
) {}
