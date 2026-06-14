package com.sumicare.dashboard.dto;

public record DashboardSummary(
        int todayBookings,
        int activeSessions,
        int completedSessions,
        int therapistsInLineup,
        long bedsOccupied
) {}
