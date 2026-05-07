package com.sumicare.biometrics;

import com.sumicare.attendance.service.AttendanceService;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class BiometricsClockInProcessor {

    private final TherapistRepository therapistRepository;
    private final AttendanceService attendanceService;

    public BiometricsClockInProcessor(TherapistRepository therapistRepository,
                                      AttendanceService attendanceService) {
        this.therapistRepository = therapistRepository;
        this.attendanceService = attendanceService;
    }

    public void process(String staffNumber, OffsetDateTime timestamp, String deviceId) {
        therapistRepository.findAll().stream()
                .filter(t -> staffNumber.equals(t.getStaffNumber()))
                .findFirst()
                .ifPresent(therapist -> apply(therapist, timestamp, deviceId));
    }

    private void apply(Therapist therapist, OffsetDateTime timestamp, String deviceId) {
        attendanceService.recordClockIn(therapist.getId(), timestamp, deviceId);
    }
}
