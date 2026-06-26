/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.service.DeckingReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/records/decking")
public class DeckingReportController {

    private final DeckingReportService deckingReportService;

    public DeckingReportController(DeckingReportService deckingReportService) {
        this.deckingReportService = deckingReportService;
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public DeckingReportService.DeckingDailyReport daily(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam String date) {
        return deckingReportService.daily(UUID.fromString(principal.organizationId()), LocalDate.parse(date));
    }

    @GetMapping("/daily/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> dailyCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam String date) {
        byte[] csv = deckingReportService.dailyCsv(UUID.fromString(principal.organizationId()), LocalDate.parse(date));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=decking-" + date + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
