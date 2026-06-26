/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.service.ReportPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final ReportPdfService reportPdfService;

    public ReportsController(ReportPdfService reportPdfService) {
        this.reportPdfService = reportPdfService;
    }

    @GetMapping(value = "/sales-summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> salesSummary(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @RequestParam String from,
                                               @RequestParam String to) {
        OffsetDateTime start = LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        byte[] pdf = reportPdfService.salesByService(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
