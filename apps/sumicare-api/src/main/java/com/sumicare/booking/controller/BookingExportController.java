package com.sumicare.booking.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingExportController {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;

    public BookingExportController(BookingRepository bookingRepository, SessionRepository sessionRepository) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam OffsetDateTime from,
                                             @RequestParam OffsetDateTime to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<Booking> bookings = bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(orgId, from, to);

        List<Session> allSessions = sessionRepository.findAllByOrganizationIdAndStartedAtBetween(orgId, from, to);
        Map<UUID, Session> sessionByBookingId = allSessions.stream()
                .collect(Collectors.toMap(Session::getBookingId, s -> s, (a, b) -> a));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("Booking ID,Status,Reservation Type,Client Nickname,Sex,Pax,Service ID,")
          .append("Scheduled At,Start Time,End Time (Expected),Ended At (Actual),")
          .append("Primary Therapist ID,Secondary Therapist ID,Room ID,Bed ID,Locker\n");

        for (Booking b : bookings) {
            Session s = sessionByBookingId.get(b.getId());
            sb.append(b.getId()).append(',')
              .append(csvCell(b.getStatus())).append(',')
              .append(csvCell(b.getReservationType())).append(',')
              .append(csvCell(b.getClientNickname())).append(',')
              .append(csvCell(b.getClientGender())).append(',')
              .append(b.getPax() != null ? b.getPax() : "").append(',')
              .append(b.getServiceId()).append(',')
              .append(b.getScheduledAt() != null ? b.getScheduledAt().format(fmt) : "").append(',')
              .append(s != null && s.getStartedAt() != null ? s.getStartedAt().format(fmt) : "").append(',')
              .append(s != null && s.getExpectedEndAt() != null ? s.getExpectedEndAt().format(fmt) : "").append(',')
              .append(s != null && s.getEndedAt() != null ? s.getEndedAt().format(fmt) : "").append(',')
              .append(s != null && s.getPrimaryTherapistId() != null ? s.getPrimaryTherapistId() : "").append(',')
              .append(s != null && s.getSecondaryTherapistId() != null ? s.getSecondaryTherapistId() : "").append(',')
              .append(s != null && s.getRoomId() != null ? s.getRoomId() : "").append(',')
              .append(s != null && s.getBedId() != null ? s.getBedId() : "").append(',')
              .append(csvCell(b.getLockerNumber()))
              .append('\n');
        }

        String filename = "bookings-" + from.toLocalDate() + "-to-" + to.toLocalDate() + ".csv";
        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    private String csvCell(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
