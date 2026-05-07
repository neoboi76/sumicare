package com.sumicare.booking.scheduler;

import com.sumicare.booking.domain.Session;
import com.sumicare.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionAutoEndJob {

    private final BookingService bookingService;

    public SessionAutoEndJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void endExpiredSessions() {
        List<Session> expired = bookingService.findExpiredActiveSessions();
        for (Session session : expired) {
            bookingService.autoEndSession(session.getId());
        }
    }
}
