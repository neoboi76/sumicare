package com.sumicare.audit.controller;

import com.sumicare.audit.domain.AuditLog;
import com.sumicare.audit.repository.AuditLogRepository;
import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public Page<AuditLog> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size,
                               @RequestParam(required = false) UUID actorUserId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        if (from != null || to != null) {
            LocalDate startDate = from != null ? from : LocalDate.now(MANILA).minusYears(10);
            LocalDate endDate = to != null ? to : LocalDate.now(MANILA);
            OffsetDateTime start = startDate.atStartOfDay(MANILA).toOffsetDateTime();
            OffsetDateTime end = endDate.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
            if (actorUserId != null) {
                return repository.findAllByOrganizationIdAndActorUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                        orgId, actorUserId, start, end, pageable);
            }
            return repository.findAllByOrganizationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                    orgId, start, end, pageable);
        }
        if (actorUserId != null) {
            return repository.findAllByOrganizationIdAndActorUserIdOrderByOccurredAtDesc(orgId, actorUserId, pageable);
        }
        return repository.findAllByOrganizationIdOrderByOccurredAtDesc(orgId, pageable);
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
