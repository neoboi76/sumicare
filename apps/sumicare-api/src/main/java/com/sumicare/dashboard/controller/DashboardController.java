package com.sumicare.dashboard.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.dashboard.dto.DashboardSummary;
import com.sumicare.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return dashboardService.summary(UUID.fromString(principal.organizationId()));
    }

    @GetMapping("/recent-reservations")
    public List<BookingResponse> recentReservations(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                    @RequestParam(required = false, defaultValue = "8") int limit) {
        return dashboardService.recentReservations(UUID.fromString(principal.organizationId()), limit);
    }
}
