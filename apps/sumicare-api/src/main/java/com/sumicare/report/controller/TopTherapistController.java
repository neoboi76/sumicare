/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.report.dto.TopTherapistResponse;
import com.sumicare.report.service.TopTherapistExcelService;
import com.sumicare.report.service.TopTherapistPdfService;
import com.sumicare.report.service.TopTherapistService;
import com.sumicare.report.service.TopTherapistService.Period;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class TopTherapistController {

    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TopTherapistService topTherapistService;
    private final TopTherapistPdfService topTherapistPdfService;
    private final TopTherapistExcelService topTherapistExcelService;

    public TopTherapistController(TopTherapistService topTherapistService,
                                  TopTherapistPdfService topTherapistPdfService,
                                  TopTherapistExcelService topTherapistExcelService) {
        this.topTherapistService = topTherapistService;
        this.topTherapistPdfService = topTherapistPdfService;
        this.topTherapistExcelService = topTherapistExcelService;
    }

    @GetMapping("/top-therapists")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TopTherapistResponse topTherapists(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "ALL") String period) {
        return topTherapistService.topTherapists(
                UUID.fromString(principal.organizationId()), parsePeriod(period));
    }

    @GetMapping("/top-therapists.pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> topTherapistsPdf(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "ALL") String period) {
        byte[] pdf = topTherapistPdfService.generate(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()),
                parsePeriod(period));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"top-therapists-" + period.toLowerCase() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/top-therapists.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> topTherapistsXlsx(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "ALL") String period) {
        byte[] data = topTherapistExcelService.generate(
                UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()),
                parsePeriod(period));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"top-therapists-" + period.toLowerCase() + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }

    private Period parsePeriod(String value) {
        try {
            return Period.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Period.ALL;
        }
    }
}
