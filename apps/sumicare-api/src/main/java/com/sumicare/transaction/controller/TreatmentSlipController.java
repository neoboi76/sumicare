package com.sumicare.transaction.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
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

    public TreatmentSlipController(TreatmentSlipService service, TreatmentSlipRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/from-session/{sessionId}")
    public TreatmentSlip generate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID sessionId) {
        return service.generateForSession(UUID.fromString(principal.organizationId()), sessionId);
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                         @RequestParam OffsetDateTime from,
                                         @RequestParam OffsetDateTime to) {
        byte[] data = service.exportToExcel(UUID.fromString(principal.organizationId()), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"treatment-slips.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
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
        return repository.findAllByOrganizationIdAndCreatedAtBetween(
                UUID.fromString(principal.organizationId()), from, to);
    }
}
