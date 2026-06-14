package com.sumicare.attendance.repository;

import com.sumicare.attendance.domain.TherapistAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TherapistAttendanceRepository extends JpaRepository<TherapistAttendance, Long> {
    List<TherapistAttendance> findAllByTherapistIdAndEventAtBetween(UUID therapistId, OffsetDateTime from, OffsetDateTime to);
    List<TherapistAttendance> findAllByEventAtBetween(OffsetDateTime from, OffsetDateTime to);
}
