package com.sumicare.therapist.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.dto.CreateTherapistRequest;
import com.sumicare.therapist.dto.TherapistResponse;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.TherapistService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/therapists")
public class TherapistController {

    private final TherapistService therapistService;
    private final TherapistRepository therapistRepository;

    public TherapistController(TherapistService therapistService, TherapistRepository therapistRepository) {
        this.therapistService = therapistService;
        this.therapistRepository = therapistRepository;
    }

    @GetMapping
    public List<TherapistResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return therapistService.listForOrganization(UUID.fromString(principal.organizationId()));
    }

    @GetMapping("/deactivated")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<TherapistResponse> listDeactivated(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return therapistService.listDeactivatedForOrganization(UUID.fromString(principal.organizationId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TherapistResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @Valid @RequestBody CreateTherapistRequest request) {
        return therapistService.create(UUID.fromString(principal.organizationId()), request);
    }

    @PatchMapping("/{therapistId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @PathVariable UUID therapistId,
                                    @RequestBody CreateTherapistRequest request) {
        return therapistService.update(UUID.fromString(principal.organizationId()), therapistId, request);
    }

    @DeleteMapping("/{therapistId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                           @PathVariable UUID therapistId) {
        requireSameOrg(principal, therapistId).setActive(false);
    }

    @PostMapping("/{therapistId}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse reactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @PathVariable UUID therapistId) {
        Therapist t = requireSameOrg(principal, therapistId);
        t.setActive(true);
        return therapistService.toResponse(t);
    }

    private Therapist requireSameOrg(AuthenticatedPrincipal principal, UUID therapistId) {
        Therapist t = therapistRepository.findById(therapistId).orElseThrow();
        if (!t.getOrganizationId().equals(UUID.fromString(principal.organizationId()))) {
            throw new AccessDeniedException("Therapist belongs to another organization.");
        }
        return t;
    }
}
