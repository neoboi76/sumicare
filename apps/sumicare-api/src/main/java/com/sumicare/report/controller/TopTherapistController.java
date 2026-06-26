/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.dto.TopTherapistResponse;
import com.sumicare.report.service.TopTherapistService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports/top-therapists")
public class TopTherapistController {

    private final TopTherapistService topTherapistService;

    public TopTherapistController(TopTherapistService topTherapistService) {
        this.topTherapistService = topTherapistService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TopTherapistResponse topTherapists(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return topTherapistService.topTherapists(UUID.fromString(principal.organizationId()));
    }
}
