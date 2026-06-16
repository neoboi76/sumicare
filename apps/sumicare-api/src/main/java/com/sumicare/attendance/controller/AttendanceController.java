/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.attendance.controller;

import com.sumicare.attendance.domain.TherapistAttendance;
import com.sumicare.attendance.repository.TherapistAttendanceRepository;
import com.sumicare.attendance.service.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final TherapistAttendanceRepository repository;
    private final AttendanceService attendanceService;

    public AttendanceController(TherapistAttendanceRepository repository, AttendanceService attendanceService) {
        this.repository = repository;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<AttendanceRecord> list(
            @RequestParam(required = false) UUID therapistId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        OffsetDateTime start = from != null ? from : OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end = to != null ? to : start.plusDays(1);

        List<TherapistAttendance> records = therapistId != null
                ? repository.findAllByTherapistIdAndEventAtBetween(therapistId, start, end)
                : repository.findAllByEventAtBetween(start, end);

        return records.stream()
                .map(r -> new AttendanceRecord(r.getId(), r.getTherapistId(), r.getEventType(),
                        r.getEventAt(), r.getDeviceId(), r.getRemarks()))
                .toList();
    }

    @PostMapping("/absence")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public TherapistAttendance recordAbsence(@RequestBody AbsenceRequest request) {
        return attendanceService.recordAbsence(request.therapistId(), OffsetDateTime.now(), request.remarks());
    }

    @PostMapping("/day-off")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public TherapistAttendance recordDayOff(@RequestBody DayOffRequest request) {
        return attendanceService.recordDayOff(request.therapistId(), OffsetDateTime.now());
    }

    public record AttendanceRecord(Long id, UUID therapistId, String eventType,
                                   OffsetDateTime eventAt, String deviceId, String remarks) {}

    public record AbsenceRequest(UUID therapistId, String remarks) {}

    public record DayOffRequest(UUID therapistId) {}
}
