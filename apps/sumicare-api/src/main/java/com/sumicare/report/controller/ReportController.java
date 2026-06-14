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
@RequestMapping("/api/reports")
public class ReportController {

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

    @GetMapping("/cutoff/export.csv")
    public ResponseEntity<byte[]> cutoffCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @RequestParam OffsetDateTime from,
                                            @RequestParam OffsetDateTime to) {
        byte[] data = reportService.exportCutoffToCsv(UUID.fromString(principal.organizationId()), from, to);
        String filename = "cutoff-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
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

    @GetMapping("/cutoff/services/export.csv")
    public ResponseEntity<byte[]> cutoffServicesCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) Long shiftId) {
        byte[] data = operationsReportService.cutoffServicesCsv(UUID.fromString(principal.organizationId()), from, to, shiftId);
        String filename = "cutoff-services-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    @GetMapping("/daily")
    public OperationsReportService.DailyReportResponse daily(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam LocalDate date) {
        return operationsReportService.daily(UUID.fromString(principal.organizationId()), date);
    }

    @GetMapping("/daily/export.csv")
    public ResponseEntity<byte[]> dailyCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam LocalDate date) {
        byte[] data = operationsReportService.dailyCsv(UUID.fromString(principal.organizationId()), date);
        String filename = "daily-" + date + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    @GetMapping("/monthly-detailed")
    public OperationsReportService.MonthlyReportResponse monthlyDetailed(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam int year,
            @RequestParam int month) {
        return operationsReportService.monthly(UUID.fromString(principal.organizationId()), year, month);
    }

    @GetMapping("/monthly-detailed/export.csv")
    public ResponseEntity<byte[]> monthlyDetailedCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam int year,
            @RequestParam int month) {
        byte[] data = operationsReportService.monthlyCsv(UUID.fromString(principal.organizationId()), year, month);
        String filename = "monthly-" + year + "-" + String.format("%02d", month) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }
}
