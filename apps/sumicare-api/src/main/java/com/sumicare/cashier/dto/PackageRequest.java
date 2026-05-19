package com.sumicare.cashier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PackageRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 2000) String benefits,
        Integer maxStayHours,
        @Min(1) @Max(12) int defaultPax,
        Boolean couple,
        Boolean includesMassage,
        Boolean bundlesPrivateRoom,
        Boolean requiresVipRoom,
        Boolean active,
        List<PackageTierRequest> tiers
) {}
