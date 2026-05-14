package com.sumicare.audit.controller;

import com.sumicare.audit.domain.AuditLog;
import com.sumicare.audit.repository.AuditLogRepository;
import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/by-target")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<AuditLog> byTarget(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestParam String entity,
                                    @RequestParam String id) {
        return repository.findAllByOrganizationIdAndTargetEntityAndTargetIdOrderByOccurredAtDesc(
                UUID.fromString(principal.organizationId()), entity, id);
    }
}
