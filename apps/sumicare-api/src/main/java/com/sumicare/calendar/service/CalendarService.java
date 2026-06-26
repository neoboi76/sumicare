/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.calendar.service;

import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.calendar.dto.CalendarEntry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarService {

    private final BookingRepository bookingRepository;

    public CalendarService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<CalendarEntry> entries(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        return bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(organizationId, from, to).stream()
                .map(b -> new CalendarEntry(b.getId(), b.getReference(), b.getClientNickname(),
                        b.getReservationType(), b.getStatus(), b.getScheduledAt()))
                .toList();
    }
}
