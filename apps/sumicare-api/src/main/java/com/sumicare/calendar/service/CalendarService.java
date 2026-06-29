/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.calendar.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.calendar.dto.CalendarEntry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CalendarService {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;

    public CalendarService(BookingRepository bookingRepository, SessionRepository sessionRepository) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<CalendarEntry> entries(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        Map<UUID, CalendarEntry> byBookingId = new LinkedHashMap<>();

        for (Booking b : bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(organizationId, from, to)) {
            if ("CANCELLED".equalsIgnoreCase(b.getStatus())) continue;
            byBookingId.put(b.getId(), toEntry(b, b.getScheduledAt()));
        }

        for (Session s : sessionRepository.findAllByOrganizationIdAndStartedAtBetween(organizationId, from, to)) {
            if (s.getBookingId() == null || s.getStartedAt() == null) continue;
            UUID bookingId = s.getBookingId();
            if (byBookingId.containsKey(bookingId)) {
                CalendarEntry existing = byBookingId.get(bookingId);
                if (s.getStartedAt().isBefore(existing.scheduledAt())) {
                    byBookingId.put(bookingId, new CalendarEntry(
                            existing.bookingId(), existing.reference(), existing.clientNickname(),
                            existing.reservationType(), existing.schedulingStatus(), s.getStartedAt()));
                }
            } else {
                bookingRepository.findById(bookingId).ifPresent(b -> {
                    if (!"CANCELLED".equalsIgnoreCase(b.getStatus())) {
                        byBookingId.put(bookingId, toEntry(b, s.getStartedAt()));
                    }
                });
            }
        }

        return new ArrayList<>(byBookingId.values());
    }

    private CalendarEntry toEntry(Booking b, OffsetDateTime displayAt) {
        return new CalendarEntry(b.getId(), b.getReference(), b.getClientNickname(),
                b.getReservationType(), schedulingStatus(b.getStatus()), displayAt);
    }

    private String schedulingStatus(String bookingStatus) {
        if (bookingStatus == null) {
            return "SCHEDULED";
        }
        return switch (bookingStatus.toUpperCase()) {
            case "COMPLETED" -> "COMPLETED";
            case "ACTIVE", "IN_PROGRESS" -> "IN_PROGRESS";
            default -> "SCHEDULED";
        };
    }
}
