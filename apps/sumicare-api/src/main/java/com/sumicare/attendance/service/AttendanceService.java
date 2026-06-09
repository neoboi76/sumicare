package com.sumicare.attendance.service;

import com.sumicare.attendance.domain.TherapistAttendance;
import com.sumicare.attendance.repository.TherapistAttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttendanceService {

    private final TherapistAttendanceRepository repository;

    public AttendanceService(TherapistAttendanceRepository repository) {
        this.repository = repository;
    }

    public Set<UUID> clockedInTherapistIds(OffsetDateTime from, OffsetDateTime to) {
        Map<UUID, TherapistAttendance> latest = new HashMap<>();
        for (TherapistAttendance a : repository.findAllByEventAtBetween(from, to)) {
            latest.merge(a.getTherapistId(), a,
                    (existing, candidate) -> candidate.getEventAt().isAfter(existing.getEventAt()) ? candidate : existing);
        }
        Set<UUID> result = new HashSet<>();
        latest.forEach((id, a) -> {
            if ("CLOCK_IN".equals(a.getEventType())) result.add(id);
        });
        return result;
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
