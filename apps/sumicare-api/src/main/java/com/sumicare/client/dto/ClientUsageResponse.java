/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.client.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ClientUsageResponse(
        UUID clientId,
        String nickname,
        String email,
        int bookingCount,
        BigDecimal totalSpending,
        List<UsageCount> topServices,
        List<UsageCount> topPackages,
        List<UsageCount> topTherapists,
        List<VoucherEligibility> vouchers
) {

    public record UsageCount(String label, long count) {}

    public record VoucherEligibility(String code, String name, BigDecimal discount, boolean eligible) {}
}
