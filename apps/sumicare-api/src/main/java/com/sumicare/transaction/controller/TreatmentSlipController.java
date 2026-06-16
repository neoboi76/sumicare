/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.print.TreatmentSlipPdfService;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.dto.UpdateTreatmentSlipRequest;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/treatment-slips")
public class TreatmentSlipController {

    private final TreatmentSlipService service;
    private final TreatmentSlipRepository repository;
    private final TreatmentSlipPdfService pdfService;

    public TreatmentSlipController(TreatmentSlipService service,
                                   TreatmentSlipRepository repository,
                                   TreatmentSlipPdfService pdfService) {
        this.service = service;
        this.repository = repository;
        this.pdfService = pdfService;
    }

    @GetMapping("/{slipId}/slip.pdf")
    public ResponseEntity<byte[]> slipPdf(@PathVariable UUID slipId) {
        byte[] data = pdfService.renderSlip(slipId);
        String filename = "slip-" + slipId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @PostMapping("/from-session/{sessionId}")
    public TreatmentSlip generate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID sessionId) {
        return service.generateForSession(UUID.fromString(principal.organizationId()), sessionId);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam OffsetDateTime from,
                                             @RequestParam OffsetDateTime to) {
        byte[] data = service.exportToCsv(UUID.fromString(principal.organizationId()), from, to);
        String filename = "treatment-slips-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    @GetMapping("/{slipId}")
    public TreatmentSlip get(@PathVariable UUID slipId) {
        return repository.findById(slipId).orElseThrow();
    }

    @GetMapping
    public List<TreatmentSlip> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestParam OffsetDateTime from,
                                    @RequestParam OffsetDateTime to) {
        return repository.findAllByOrganizationIdAndScheduleBetween(
                UUID.fromString(principal.organizationId()), from, to);
    }

    @PatchMapping("/{slipId}")
    public TreatmentSlip update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @PathVariable UUID slipId,
                                @Valid @RequestBody UpdateTreatmentSlipRequest request) {
        return service.update(UUID.fromString(principal.organizationId()), slipId, request);
    }
}
