/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.service.CommissionReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/records/commissions")
public class CommissionReportController {

    private final CommissionReportService service;

    public CommissionReportController(CommissionReportService service) {
        this.service = service;
    }

    @GetMapping("/shift")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public CommissionReportService.ShiftReport shift(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                      @RequestParam Long shiftId,
                                                      @RequestParam LocalDate date) {
        return service.shift(UUID.fromString(principal.organizationId()), shiftId, date);
    }

    @GetMapping("/shift/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> shiftCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @RequestParam Long shiftId,
                                            @RequestParam LocalDate date) {
        byte[] data = service.shiftCsv(UUID.fromString(principal.organizationId()), shiftId, date);
        String filename = "commissions-shift-" + shiftId + "-" + date + ".csv";
        return csvResponse(filename, data);
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public CommissionReportService.DailyReport daily(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                      @RequestParam LocalDate date) {
        return service.daily(UUID.fromString(principal.organizationId()), date);
    }

    @GetMapping("/daily/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> dailyCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @RequestParam LocalDate date) {
        byte[] data = service.dailyCsv(UUID.fromString(principal.organizationId()), date);
        return csvResponse("commissions-daily-" + date + ".csv", data);
    }

    @GetMapping("/cutoff")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public CommissionReportService.MatrixReport cutoff(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                        @RequestParam int year,
                                                        @RequestParam int month,
                                                        @RequestParam(defaultValue = "1") int half) {
        return service.cutoff(UUID.fromString(principal.organizationId()), year, month, half);
    }

    @GetMapping("/cutoff/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> cutoffCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam int year,
                                             @RequestParam int month,
                                             @RequestParam(defaultValue = "1") int half) {
        byte[] data = service.cutoffCsv(UUID.fromString(principal.organizationId()), year, month, half);
        String filename = "commissions-cutoff-" + year + "-" + String.format("%02d", month) + "-h" + half + ".csv";
        return csvResponse(filename, data);
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public CommissionReportService.MatrixReport monthly(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                         @RequestParam int year,
                                                         @RequestParam int month) {
        return service.monthly(UUID.fromString(principal.organizationId()), year, month);
    }

    @GetMapping("/monthly/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> monthlyCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                              @RequestParam int year,
                                              @RequestParam int month) {
        byte[] data = service.monthlyCsv(UUID.fromString(principal.organizationId()), year, month);
        String filename = "commissions-monthly-" + year + "-" + String.format("%02d", month) + ".csv";
        return csvResponse(filename, data);
    }

    @GetMapping("/tips")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public CommissionReportService.TipReport tips(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                  @RequestParam LocalDate from,
                                                  @RequestParam LocalDate to,
                                                  @RequestParam(required = false) UUID therapistId) {
        return service.tips(UUID.fromString(principal.organizationId()), from, to, therapistId);
    }

    @GetMapping("/tips/export.csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> tipsCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @RequestParam LocalDate from,
                                          @RequestParam LocalDate to,
                                          @RequestParam(required = false) UUID therapistId) {
        byte[] data = service.tipsCsv(UUID.fromString(principal.organizationId()), from, to, therapistId);
        return csvResponse("tips-" + from + "-to-" + to + ".csv", data);
    }

    private ResponseEntity<byte[]> csvResponse(String filename, byte[] data) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }
}
