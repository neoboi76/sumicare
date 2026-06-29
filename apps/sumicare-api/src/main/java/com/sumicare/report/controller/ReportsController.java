/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.feedback.service.SurveyAnalyticsService.LasemaSatisfactionStats;
import com.sumicare.report.service.RegisteredClientsReportService;
import com.sumicare.report.service.ReportPdfService;
import com.sumicare.report.service.SalesGroupBy;
import com.sumicare.report.service.SatisfactionReportService;
import com.sumicare.report.service.TherapistPerformancePdfService;
import com.sumicare.report.service.TherapistPerformanceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportPdfService reportPdfService;
    private final TherapistPerformanceService therapistPerformanceService;
    private final TherapistPerformancePdfService therapistPerformancePdfService;
    private final RegisteredClientsReportService registeredClientsReportService;
    private final SatisfactionReportService satisfactionReportService;

    public ReportsController(ReportPdfService reportPdfService,
                             TherapistPerformanceService therapistPerformanceService,
                             TherapistPerformancePdfService therapistPerformancePdfService,
                             RegisteredClientsReportService registeredClientsReportService,
                             SatisfactionReportService satisfactionReportService) {
        this.reportPdfService = reportPdfService;
        this.therapistPerformanceService = therapistPerformanceService;
        this.therapistPerformancePdfService = therapistPerformancePdfService;
        this.registeredClientsReportService = registeredClientsReportService;
        this.satisfactionReportService = satisfactionReportService;
    }

    @GetMapping("/therapist-performance")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TherapistPerformanceService.TherapistPerformanceReport therapistPerformance(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam String from,
            @RequestParam String to) {
        return therapistPerformanceService.report(UUID.fromString(principal.organizationId()),
                LocalDate.parse(from), LocalDate.parse(to));
    }

    @GetMapping("/therapist-performance/{therapistId}/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> therapistPerformancePdf(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID therapistId,
            @RequestParam String from,
            @RequestParam String to) {
        byte[] pdf = therapistPerformancePdfService.generate(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()),
                therapistId,
                LocalDate.parse(from), LocalDate.parse(to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"therapist-performance-" + therapistId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/registered-clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public RegisteredClientsReportService.RegisteredClientsReport registeredClients(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return registeredClientsReportService.report(UUID.fromString(principal.organizationId()));
    }

    @GetMapping(value = "/registered-clients.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> registeredClientsPdf(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        byte[] pdf = registeredClientsReportService.pdf(
                UUID.fromString(principal.organizationId()), UUID.fromString(principal.userId()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registered-clients.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/registered-clients.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> registeredClientsXlsx(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        byte[] data = registeredClientsReportService.xlsx(
                UUID.fromString(principal.organizationId()), UUID.fromString(principal.userId()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registered-clients.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }

    @GetMapping(value = "/sales-summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> salesSummary(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @RequestParam String from,
                                               @RequestParam String to,
                                               @RequestParam(defaultValue = "SERVICE") String groupBy) {
        OffsetDateTime start = LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        SalesGroupBy groupByEnum;
        try {
            groupByEnum = SalesGroupBy.valueOf(groupBy.toUpperCase());
        } catch (IllegalArgumentException e) {
            groupByEnum = SalesGroupBy.SERVICE;
        }
        byte[] pdf = reportPdfService.salesByService(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), start, end, groupByEnum);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/satisfaction")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public LasemaSatisfactionStats satisfaction(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam String from,
            @RequestParam String to) {
        OffsetDateTime start = LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        return satisfactionReportService.stats(UUID.fromString(principal.organizationId()), start, end);
    }

    @GetMapping(value = "/satisfaction.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> satisfactionPdf(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam String from,
            @RequestParam String to) {
        OffsetDateTime start = LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        byte[] pdf = satisfactionReportService.pdf(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), start, end);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"satisfaction-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
