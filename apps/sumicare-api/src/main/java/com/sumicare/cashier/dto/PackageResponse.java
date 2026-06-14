package com.sumicare.cashier.dto;

import java.util.List;

public record PackageResponse(
        Long id,
        String code,
        String name,
        String description,
        String benefits,
        Integer maxStayHours,
        int defaultPax,
        boolean couple,
        boolean includesMassage,
        boolean bundlesPrivateRoom,
        boolean requiresVipRoom,
        boolean active,
        List<String> inclusions,
        List<PackageTierResponse> tiers
) {}
