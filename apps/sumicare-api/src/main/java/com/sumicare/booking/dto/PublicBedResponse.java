package com.sumicare.booking.dto;

import java.util.Map;
import java.util.UUID;

public record PublicBedResponse(
        UUID id,
        String label,
        Integer rowIndex,
        Map<String, String> occupancy
) {}
