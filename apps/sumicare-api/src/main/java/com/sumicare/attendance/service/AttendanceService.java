package com.sumicare.attendance.service;

import com.sumicare.attendance.domain.TherapistAttendance;
import com.sumicare.attendance.repository.TherapistAttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AttendanceService {

    private final TherapistAttendanceRepository repository;

    public AttendanceService(TherapistAttendanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TherapistAttendance recordClockIn(UUID therapistId, OffsetDateTime at, String deviceId) {
        TherapistAttendance entry = new TherapistAttendance();
        entry.setTherapistId(therapistId);
        entry.setEventType("CLOCK_IN");
        entry.setEventAt(at);
        entry.setDeviceId(deviceId);
        return repository.save(entry);
    }

    @Transactional
    public TherapistAttendance recordClockOut(UUID therapistId, OffsetDateTime at, String deviceId) {
        TherapistAttendance entry = new TherapistAttendance();
        entry.setTherapistId(therapistId);
        entry.setEventType("CLOCK_OUT");
        entry.setEventAt(at);
        entry.setDeviceId(deviceId);
        return repository.save(entry);
    }

    @Transactional
    public TherapistAttendance recordAbsence(UUID therapistId, OffsetDateTime at, String remarks) {
        TherapistAttendance entry = new TherapistAttendance();
        entry.setTherapistId(therapistId);
        entry.setEventType("ABSENT");
        entry.setEventAt(at);
        entry.setRemarks(remarks);
        return repository.save(entry);
    }

    @Transactional
    public TherapistAttendance recordDayOff(UUID therapistId, OffsetDateTime at) {
        TherapistAttendance entry = new TherapistAttendance();
        entry.setTherapistId(therapistId);
        entry.setEventType("DAY_OFF");
        entry.setEventAt(at);
        return repository.save(entry);
    }
}
