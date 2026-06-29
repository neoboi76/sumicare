/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
