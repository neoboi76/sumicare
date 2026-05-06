package com.sumicare.audit.controller;

import com.sumicare.audit.domain.AuditLog;
import com.sumicare.audit.repository.AuditLogRepository;
import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public Page<AuditLog> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return repository.findAllByOrganizationIdOrderByOccurredAtDesc(
                UUID.fromString(principal.organizationId()),
                PageRequest.of(page, Math.min(size, 200)));
    }
}
