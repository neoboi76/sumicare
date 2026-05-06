package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.domain.DayReport;
import com.sumicare.report.domain.MonthlyReport;
import com.sumicare.report.repository.DayReportRepository;
import com.sumicare.report.repository.MonthlyReportRepository;
import com.sumicare.report.service.ReportAggregationService;
import com.sumicare.report.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

    public ReportController(ReportService reportService,
                            ReportAggregationService aggregationService,
                            DayReportRepository dayReportRepository,
                            MonthlyReportRepository monthlyReportRepository) {
        this.reportService = reportService;
        this.aggregationService = aggregationService;
        this.dayReportRepository = dayReportRepository;
        this.monthlyReportRepository = monthlyReportRepository;
    }

    @GetMapping("/cutoff")
    public ReportService.ReportSummary cutoff(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                              @RequestParam OffsetDateTime from,
                                              @RequestParam OffsetDateTime to) {
        return reportService.buildCutoffReport(UUID.fromString(principal.organizationId()), from, to);
    }

    @GetMapping("/cutoff/export")
    public ResponseEntity<byte[]> cutoffExcel(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                              @RequestParam OffsetDateTime from,
                                              @RequestParam OffsetDateTime to) throws IOException {
        byte[] data = reportService.exportCutoffToExcel(UUID.fromString(principal.organizationId()), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cutoff.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
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
}
