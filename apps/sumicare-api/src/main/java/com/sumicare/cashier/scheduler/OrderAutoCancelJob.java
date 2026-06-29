/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.scheduler;

import com.sumicare.cashier.service.OrderService;
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
public class OrderAutoCancelJob {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCancelJob.class);

    private final OrganizationRepository organizationRepository;
    private final OrderService orderService;

    public OrderAutoCancelJob(OrganizationRepository organizationRepository, OrderService orderService) {
        this.organizationRepository = organizationRepository;
        this.orderService = orderService;
    }

    // Runs just after Manila midnight so the previous business day is fully closed before
    // sweeping; pending orders whose scheduled date has elapsed without a session are cancelled.
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Manila")
    public void sweep() {
        UsernamePasswordAuthenticationToken systemAuth = new UsernamePasswordAuthenticationToken(
                "system", null, List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        SecurityContextHolder.getContext().setAuthentication(systemAuth);
        try {
            organizationRepository.findAll().forEach(org -> {
                try {
                    int cancelled = orderService.autoCancelElapsedOrders(org.getId());
                    if (cancelled > 0) {
                        log.info("OrderAutoCancelJob cancelled {} elapsed order(s) for org {}", cancelled, org.getId());
                    }
                } catch (Exception ex) {
                    log.error("OrderAutoCancelJob failed for org {}", org.getId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("OrderAutoCancelJob top-level failure", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
