/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.domain.DayReport;
import com.sumicare.report.domain.MonthlyReport;
import com.sumicare.report.repository.DayReportRepository;
import com.sumicare.report.repository.MonthlyReportRepository;
import com.sumicare.report.service.OperationsReportService;
import com.sumicare.report.service.ReportAggregationService;
import com.sumicare.report.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/records")
public class ReportController {

    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ReportAggregationService aggregationService;
    private final DayReportRepository dayReportRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final OperationsReportService operationsReportService;

    public ReportController(ReportService reportService,
                            ReportAggregationService aggregationService,
                            DayReportRepository dayReportRepository,
                            MonthlyReportRepository monthlyReportRepository,
                            OperationsReportService operationsReportService) {
        this.reportService = reportService;
        this.aggregationService = aggregationService;
        this.dayReportRepository = dayReportRepository;
        this.monthlyReportRepository = monthlyReportRepository;
        this.operationsReportService = operationsReportService;
    }

    @GetMapping("/cutoff")
    public ReportService.ReportSummary cutoff(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                              @RequestParam OffsetDateTime from,
                                              @RequestParam OffsetDateTime to) {
        return reportService.buildCutoffReport(UUID.fromString(principal.organizationId()), from, to);
    }

    @GetMapping("/cutoff/export.xlsx")
    public ResponseEntity<byte[]> cutoffXlsx(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam OffsetDateTime from,
                                             @RequestParam OffsetDateTime to) {
        byte[] data = reportService.exportCutoffToXlsx(UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), from, to);
        String filename = "cutoff-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".xlsx";
        return xlsxResponse(filename, data);
    }

    @GetMapping("/day")
    public List<DayReport> dayReports(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestParam LocalDate from,
                                      @RequestParam LocalDate to) {
        return dayReportRepository.findAllByOrganizationIdAndReportDateBetweenOrderByReportDateDesc(
                UUID.fromString(principal.organizationId()), from, to);
    }

    @PostMapping("/day/regenerate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public DayReport regenerateDay(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                   @RequestParam LocalDate date) {
        return aggregationService.generateDayReport(UUID.fromString(principal.organizationId()), date);
    }

    @GetMapping("/monthly")
    public List<MonthlyReport> monthlyReports(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return monthlyReportRepository.findAllByOrganizationIdOrderByReportYearDescReportMonthDesc(
                UUID.fromString(principal.organizationId()));
    }

    @PostMapping("/monthly/regenerate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public MonthlyReport regenerateMonth(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                         @RequestParam int year,
                                         @RequestParam int month) {
        return aggregationService.generateMonthlyReport(
                UUID.fromString(principal.organizationId()), YearMonth.of(year, month));
    }

    @GetMapping("/cutoff/services")
    public OperationsReportService.CutoffServicesReport cutoffServices(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) Long shiftId) {
        return operationsReportService.cutoffServices(UUID.fromString(principal.organizationId()), from, to, shiftId);
    }

    @GetMapping("/cutoff/services/export.xlsx")
    public ResponseEntity<byte[]> cutoffServicesXlsx(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) Long shiftId) {
        byte[] data = operationsReportService.cutoffServicesXlsx(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), from, to, shiftId);
        String filename = "cutoff-services-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".xlsx";
        return xlsxResponse(filename, data);
    }

    @GetMapping("/daily")
    public OperationsReportService.DailyReportResponse daily(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam LocalDate date) {
        return operationsReportService.daily(UUID.fromString(principal.organizationId()), date);
    }

    @GetMapping("/daily/export.xlsx")
    public ResponseEntity<byte[]> dailyXlsx(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam LocalDate date) {
        byte[] data = operationsReportService.dailyXlsx(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), date);
        return xlsxResponse("daily-" + date + ".xlsx", data);
    }

    @GetMapping("/monthly-detailed")
    public OperationsReportService.MonthlyReportResponse monthlyDetailed(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam int year,
            @RequestParam int month) {
        return operationsReportService.monthly(UUID.fromString(principal.organizationId()), year, month);
    }

    @GetMapping("/monthly-detailed/export.xlsx")
    public ResponseEntity<byte[]> monthlyDetailedXlsx(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam int year,
            @RequestParam int month) {
        byte[] data = operationsReportService.monthlyXlsx(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), year, month);
        String filename = "monthly-" + year + "-" + String.format("%02d", month) + ".xlsx";
        return xlsxResponse(filename, data);
    }

    private ResponseEntity<byte[]> xlsxResponse(String filename, byte[] data) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }
}
