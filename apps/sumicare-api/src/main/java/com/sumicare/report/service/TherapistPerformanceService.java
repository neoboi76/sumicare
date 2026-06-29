/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.feedback.service.SurveyAnalyticsService;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.Commission;
import com.sumicare.transaction.domain.TherapistTip;
import com.sumicare.transaction.repository.CommissionRepository;
import com.sumicare.transaction.repository.TherapistTipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TherapistPerformanceService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final SessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final ServiceRepository serviceRepository;
    private final TherapistRepository therapistRepository;
    private final CommissionRepository commissionRepository;
    private final TherapistTipRepository tipRepository;
    private final SurveyAnalyticsService surveyAnalyticsService;

    public TherapistPerformanceService(SessionRepository sessionRepository,
                                       BookingRepository bookingRepository,
                                       OrderRepository orderRepository,
                                       ServiceRepository serviceRepository,
                                       TherapistRepository therapistRepository,
                                       CommissionRepository commissionRepository,
                                       TherapistTipRepository tipRepository,
                                       SurveyAnalyticsService surveyAnalyticsService) {
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
        this.commissionRepository = commissionRepository;
        this.tipRepository = tipRepository;
        this.surveyAnalyticsService = surveyAnalyticsService;
    }

    public record NameCount(String name, long count) {}

    public record TherapistPerformance(
            UUID therapistId,
            String nickname,
            BigDecimal revenue,
            BigDecimal commissions,
            BigDecimal tips,
            long servicesRendered,
            long specificRequests,
            List<NameCount> topClients,
            List<NameCount> topServices,
            double averageSatisfactionRating,
            Map<String, Double> perCriterionAverages,
            double satisfactionIndex,
            List<String> recentComments
    ) {}

    public record TherapistPerformanceReport(LocalDate from, LocalDate to, List<TherapistPerformance> therapists) {}

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TherapistPerformanceReport report(UUID organizationId, LocalDate from, LocalDate to) {
        OffsetDateTime start = from.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();

        Map<UUID, Accumulator> byTherapist = new HashMap<>();
        Map<UUID, Booking> bookingCache = new HashMap<>();
        Map<UUID, Order> orderByBooking = new HashMap<>();
        Map<Long, String> serviceNames = new HashMap<>();

        for (Session session : sessionRepository.findAllByOrganizationIdAndStartedAtBetween(organizationId, start, end)) {
            if ("CANCELLED".equalsIgnoreCase(session.getStatus())) continue;

            Booking booking = session.getBookingId() == null ? null
                    : bookingCache.computeIfAbsent(session.getBookingId(),
                        id -> bookingRepository.findById(id).orElse(null));
            String clientName = booking == null || booking.getClientNickname() == null
                    ? "(Walk-in)" : booking.getClientNickname();
            String serviceName = booking == null || booking.getServiceId() == null ? "(Service)"
                    : serviceNames.computeIfAbsent(booking.getServiceId(),
                        sid -> serviceRepository.findById(sid).map(s -> s.getName()).orElse("(Service)"));
            Order order = booking == null ? null
                    : orderByBooking.computeIfAbsent(booking.getId(),
                        id -> orderRepository.findByBookingId(id).orElse(null));

            for (UUID therapistId : therapistsOf(session)) {
                Accumulator acc = byTherapist.computeIfAbsent(therapistId, k -> new Accumulator());
                acc.servicesRendered++;
                if (session.isSpecificallyRequested()) acc.specificRequests++;
                acc.clientCounts.merge(clientName, 1L, Long::sum);
                acc.serviceCounts.merge(serviceName, 1L, Long::sum);
                if (order != null && order.getTotal() != null && acc.countedOrders.add(order.getId())) {
                    acc.revenue = acc.revenue.add(order.getTotal());
                }
            }
        }

        for (Commission c : commissionRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, start, end)) {
            byTherapist.computeIfAbsent(c.getTherapistId(), k -> new Accumulator()).commissions =
                    byTherapist.get(c.getTherapistId()).commissions.add(c.getAmount());
        }
        for (TherapistTip t : tipRepository.findAllByOrganizationIdAndRecordedAtBetween(organizationId, start, end)) {
            byTherapist.computeIfAbsent(t.getTherapistId(), k -> new Accumulator()).tips =
                    byTherapist.get(t.getTherapistId()).tips.add(t.getAmount());
        }

        List<TherapistPerformance> rows = new ArrayList<>();
        for (Map.Entry<UUID, Accumulator> e : byTherapist.entrySet()) {
            UUID therapistId = e.getKey();
            Accumulator acc = e.getValue();
            String nickname = therapistRepository.findById(therapistId)
                    .map(t -> t.getNickname()).orElse("Therapist");

            SurveyAnalyticsService.TherapistSatisfactionStats stats =
                    surveyAnalyticsService.therapistStats(organizationId, therapistId, start, end);

            rows.add(new TherapistPerformance(
                    therapistId, nickname, acc.revenue, acc.commissions, acc.tips,
                    acc.servicesRendered, acc.specificRequests,
                    topFive(acc.clientCounts), topFive(acc.serviceCounts),
                    stats.overallDistribution().average(),
                    stats.perCriterionAverages(),
                    stats.satisfactionIndex(),
                    stats.recentComments()
            ));
        }
        rows.sort(Comparator.comparing(TherapistPerformance::revenue).reversed());
        return new TherapistPerformanceReport(from, to, rows);
    }

    private List<UUID> therapistsOf(Session session) {
        List<UUID> ids = new ArrayList<>();
        if (session.getPrimaryTherapistId() != null) ids.add(session.getPrimaryTherapistId());
        if (session.getSecondaryTherapistId() != null) ids.add(session.getSecondaryTherapistId());
        return ids;
    }

    private List<NameCount> topFive(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NameCount::count).reversed())
                .limit(5)
                .toList();
    }

    private static class Accumulator {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal commissions = BigDecimal.ZERO;
        BigDecimal tips = BigDecimal.ZERO;
        long servicesRendered = 0;
        long specificRequests = 0;
        Set<UUID> countedOrders = new HashSet<>();
        Map<String, Long> clientCounts = new HashMap<>();
        Map<String, Long> serviceCounts = new HashMap<>();
    }
}
