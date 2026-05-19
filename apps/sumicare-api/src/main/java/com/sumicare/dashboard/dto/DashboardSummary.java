package com.sumicare.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummary(
        int todayBookings,
        int activeSessions,
        int therapistsInLineup,
        long bedsOccupied,
        BigDecimal cashOnHand
) {}
