/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.dashboard.service;

import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.service.BookingService;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.dashboard.dto.DashboardSummary;
import com.sumicare.room.service.RoomOccupancyService;
import com.sumicare.therapist.service.DeckingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final BookingService bookingService;
    private final SessionRepository sessionRepository;
    private final DeckingService deckingService;
    private final RoomOccupancyService roomOccupancyService;

    public DashboardService(BookingService bookingService,
                            SessionRepository sessionRepository,
                            DeckingService deckingService,
                            RoomOccupancyService roomOccupancyService) {
        this.bookingService = bookingService;
        this.sessionRepository = sessionRepository;
        this.deckingService = deckingService;
        this.roomOccupancyService = roomOccupancyService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<BookingResponse> recentReservations(UUID organizationId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 25);
        return bookingService.recentReservations(organizationId, capped);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public DashboardSummary summary(UUID organizationId) {
        LocalDate today = LocalDate.now(MANILA);
        OffsetDateTime dayStart = today.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();

        int todayBookings = bookingService.listBookingsForDay(organizationId, dayStart, dayEnd).size();
        int activeSessions = (int) sessionRepository.countByOrganizationIdAndStatus(organizationId, "ACTIVE");
        int completedSessions = (int) sessionRepository
                .countByOrganizationIdAndStatusAndEndedAtBetween(organizationId, "COMPLETED", dayStart, dayEnd);
        int therapistsInLineup = deckingService.currentLineup(organizationId).size();
        long bedsOccupied = roomOccupancyService.countOccupiedBeds(organizationId);

        return new DashboardSummary(
                todayBookings,
                activeSessions,
                completedSessions,
                therapistsInLineup,
                bedsOccupied
        );
    }
}
