package com.sumicare.therapist.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.dto.CreateTherapistRequest;
import com.sumicare.therapist.dto.TherapistResponse;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.TherapistService;
import jakarta.validation.Valid;
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

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TherapistResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @Valid @RequestBody CreateTherapistRequest request) {
        return therapistService.create(UUID.fromString(principal.organizationId()), request);
    }

    @PatchMapping("/{therapistId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse update(@PathVariable UUID therapistId, @RequestBody CreateTherapistRequest request) {
        Therapist t = therapistRepository.findById(therapistId).orElseThrow();
        if (request.staffNumber() != null) t.setStaffNumber(request.staffNumber());
        if (request.nickname() != null) t.setNickname(request.nickname());
        if (request.gender() != null) t.setGender(request.gender());
        t.setBackup(request.backup());
        return therapistService.toResponse(t);
    }

    @DeleteMapping("/{therapistId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivate(@PathVariable UUID therapistId) {
        Therapist t = therapistRepository.findById(therapistId).orElseThrow();
        t.setActive(false);
    }
}
