package com.sumicare.therapist.service;

import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.domain.ShiftAssignment;
import com.sumicare.shift.repository.ShiftAssignmentRepository;
import com.sumicare.shift.repository.ShiftRepository;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.dto.CreateTherapistRequest;
import com.sumicare.therapist.dto.TherapistResponse;
import com.sumicare.therapist.repository.TherapistRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TherapistService {

    private final TherapistRepository therapistRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;

    public TherapistService(TherapistRepository therapistRepository,
                            ShiftAssignmentRepository shiftAssignmentRepository,
                            ShiftRepository shiftRepository) {
        this.therapistRepository = therapistRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
    }

    public List<TherapistResponse> listForOrganization(UUID organizationId) {
        Map<Long, Shift> shifts = loadShifts(organizationId);
        return therapistRepository.findAllByOrganizationIdAndActiveTrue(organizationId).stream()
                .map(t -> toResponse(t, shifts)).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse create(UUID organizationId, CreateTherapistRequest request) {
        if (request.staffNumber() != null && !request.staffNumber().isBlank()
                && therapistRepository.existsByOrganizationIdAndStaffNumber(organizationId, request.staffNumber())) {
            throw new IllegalArgumentException("Staff number '" + request.staffNumber() + "' is already in use");
        }
        if (therapistRepository.existsByOrganizationIdAndNickname(organizationId, request.nickname())) {
            throw new IllegalArgumentException("Nickname '" + request.nickname() + "' is already in use");
        }
        Therapist t = new Therapist();
        t.setOrganizationId(organizationId);
        t.setStaffNumber(request.staffNumber());
        t.setNickname(request.nickname());
        t.setGender(request.gender());
        t.setBackup(request.backup());
        t.setActive(true);
        therapistRepository.save(t);

        if (request.shiftId() != null) {
            shiftRepository.findById(request.shiftId()).ifPresent(s -> {
                if (!shiftAssignmentRepository.existsByShiftIdAndTherapistId(s.getId(), t.getId())) {
                    ShiftAssignment sa = new ShiftAssignment();
                    sa.setShiftId(s.getId());
                    sa.setTherapistId(t.getId());
                    shiftAssignmentRepository.save(sa);
                }
            });
        }

        return toResponse(t);
    }

    public List<TherapistResponse> listDeactivatedForOrganization(UUID organizationId) {
        Map<Long, Shift> shifts = loadShifts(organizationId);
        return therapistRepository.findAllByOrganizationIdAndActiveFalse(organizationId).stream()
                .map(t -> toResponse(t, shifts)).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public TherapistResponse update(UUID organizationId, UUID therapistId, CreateTherapistRequest request) {
        Therapist t = therapistRepository.findById(therapistId).orElseThrow();

        if (request.staffNumber() != null && !request.staffNumber().equals(t.getStaffNumber())) {
            if (!request.staffNumber().isBlank()
                    && therapistRepository.existsByOrganizationIdAndStaffNumber(organizationId, request.staffNumber())) {
                throw new IllegalArgumentException("Staff number '" + request.staffNumber() + "' is already in use");
            }
            t.setStaffNumber(request.staffNumber());
        }
        if (request.nickname() != null && !request.nickname().equals(t.getNickname())) {
            if (therapistRepository.existsByOrganizationIdAndNickname(organizationId, request.nickname())) {
                throw new IllegalArgumentException("Nickname '" + request.nickname() + "' is already in use");
            }
            t.setNickname(request.nickname());
        }
        if (request.gender() != null) t.setGender(request.gender());
        t.setBackup(request.backup());

        if (request.shiftId() != null) {
            shiftAssignmentRepository.deleteAllByTherapistId(t.getId());
            shiftRepository.findById(request.shiftId()).ifPresent(s -> {
                ShiftAssignment sa = new ShiftAssignment();
                sa.setShiftId(s.getId());
                sa.setTherapistId(t.getId());
                shiftAssignmentRepository.save(sa);
            });
        }

        return toResponse(t);
    }

    public Therapist requireById(UUID id) {
        return therapistRepository.findById(id).orElseThrow();
    }

    public TherapistResponse toResponse(Therapist t) {
        return toResponse(t, loadShifts(t.getOrganizationId()));
    }

    private TherapistResponse toResponse(Therapist t, Map<Long, Shift> shifts) {
        Long shiftId = null;
        String label = null;
        for (ShiftAssignment sa : shiftAssignmentRepository.findAllByTherapistId(t.getId())) {
            Shift s = shifts.get(sa.getShiftId());
            if (s != null) {
                shiftId = s.getId();
                label = s.getLabel() + " (" + s.getStartTime() + "–" + s.getEndTime() + ")";
                break;
            }
        }
        return new TherapistResponse(t.getId(), t.getStaffNumber(), t.getNickname(),
                t.getGender(), t.isBackup(), t.isActive(), shiftId, label);
    }

    private Map<Long, Shift> loadShifts(UUID organizationId) {
        Map<Long, Shift> shifts = new HashMap<>();
        shiftRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .forEach(s -> shifts.put(s.getId(), s));
        return shifts;
    }
}
