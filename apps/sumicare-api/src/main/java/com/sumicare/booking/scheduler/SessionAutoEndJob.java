package com.sumicare.booking.scheduler;

import com.sumicare.booking.service.BookingService;
import com.sumicare.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionAutoEndJob {

    private static final Logger log = LoggerFactory.getLogger(SessionAutoEndJob.class);

    private final OrganizationRepository organizationRepository;
    private final BookingService bookingService;

    public SessionAutoEndJob(OrganizationRepository organizationRepository, BookingService bookingService) {
        this.organizationRepository = organizationRepository;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void sweep() {
        UsernamePasswordAuthenticationToken systemAuth = new UsernamePasswordAuthenticationToken(
                "system", null, List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        SecurityContextHolder.getContext().setAuthentication(systemAuth);
        try {
            organizationRepository.findAll().forEach(org -> {
                try {
                    int ended = bookingService.autoEndExpiredSessions(org.getId());
                    if (ended > 0) {
                        log.info("SessionAutoEndJob ended {} session(s) for org {}", ended, org.getId());
                    }
                } catch (Exception ex) {
                    log.error("SessionAutoEndJob failed for org {}", org.getId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("SessionAutoEndJob top-level failure", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
