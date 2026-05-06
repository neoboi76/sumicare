package com.sumicare.biometrics;

import com.sumicare.attendance.service.AttendanceService;
import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.service.ShiftService;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.DeckingService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class BiometricsClockInProcessor {

    private final TherapistRepository therapistRepository;
    private final AttendanceService attendanceService;
    private final ShiftService shiftService;
    private final DeckingService deckingService;

    public BiometricsClockInProcessor(TherapistRepository therapistRepository,
                                      AttendanceService attendanceService,
                                      ShiftService shiftService,
                                      DeckingService deckingService) {
        this.therapistRepository = therapistRepository;
        this.attendanceService = attendanceService;
        this.shiftService = shiftService;
        this.deckingService = deckingService;
    }

    public void process(String staffNumber, OffsetDateTime timestamp, String deviceId) {
        therapistRepository.findAll().stream()
                .filter(t -> staffNumber.equals(t.getStaffNumber()))
                .findFirst()
                .ifPresent(therapist -> apply(therapist, timestamp, deviceId));
    }

    private void apply(Therapist therapist, OffsetDateTime timestamp, String deviceId) {
        attendanceService.recordClockIn(therapist.getId(), timestamp, deviceId);
        Optional<Shift> active = shiftService.resolveActiveShiftFor(therapist.getOrganizationId(),
                timestamp.toLocalTime());
        active.ifPresent(shift -> deckingService.appendToBack(therapist.getOrganizationId(),
                therapist.getId(), shift.getId()));
    }
}
