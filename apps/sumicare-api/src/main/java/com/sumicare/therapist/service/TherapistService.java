package com.sumicare.therapist.service;

import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.dto.CreateTherapistRequest;
import com.sumicare.therapist.dto.TherapistResponse;
import com.sumicare.therapist.repository.TherapistRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TherapistService {

    private final TherapistRepository therapistRepository;

    public TherapistService(TherapistRepository therapistRepository) {
        this.therapistRepository = therapistRepository;
    }

    public List<TherapistResponse> listForOrganization(UUID organizationId) {
        return therapistRepository.findAllByOrganizationIdAndActiveTrue(organizationId).stream()
                .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse create(UUID organizationId, CreateTherapistRequest request) {
        Therapist t = new Therapist();
        t.setOrganizationId(organizationId);
        t.setStaffNumber(request.staffNumber());
        t.setNickname(request.nickname());
        t.setGender(request.gender());
        t.setBackup(request.backup());
        t.setActive(true);
        therapistRepository.save(t);
        return toResponse(t);
    }

    public Therapist requireById(UUID id) {
        return therapistRepository.findById(id).orElseThrow();
    }

    public TherapistResponse toResponse(Therapist t) {
        return new TherapistResponse(t.getId(), t.getStaffNumber(), t.getNickname(),
                t.getGender(), t.isBackup(), t.isActive());
    }
}
