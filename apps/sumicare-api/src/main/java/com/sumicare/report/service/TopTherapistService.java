/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.report.dto.TopTherapistResponse;
import com.sumicare.report.dto.TopTherapistResponse.Entry;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TopTherapistService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final OffsetDateTime EPOCH = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    private final TherapistRepository therapistRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;

    public TopTherapistService(TherapistRepository therapistRepository,
                               SessionRepository sessionRepository,
                               FeedbackRepository feedbackRepository) {
        this.therapistRepository = therapistRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public enum Period { WEEKLY, MONTHLY, PAYROLL, ALL }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public TopTherapistResponse topTherapists(UUID organizationId, Period period) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = resolveFrom(period, now);

        Map<UUID, Long> serviceCounts = new HashMap<>();
        Map<UUID, Long> requestCounts = new HashMap<>();
        for (Session session : sessionRepository.findAllByOrganizationIdAndStartedAtBetween(organizationId, from, now)) {
            UUID therapistId = session.getPrimaryTherapistId();
            if (therapistId == null || "CANCELLED".equals(session.getStatus())) continue;
            serviceCounts.merge(therapistId, 1L, Long::sum);
            if (session.isSpecificallyRequested()) {
                requestCounts.merge(therapistId, 1L, Long::sum);
            }
        }

        Map<UUID, long[]> ratingTotals = new HashMap<>();
        for (Feedback feedback : feedbackRepository.findAllByOrganizationIdAndSubmittedAtBetweenOrderBySubmittedAtAsc(organizationId, from, now)) {
            if (!"THERAPIST".equals(feedback.getFeedbackType()) || feedback.getTherapistId() == null) continue;
            long[] acc = ratingTotals.computeIfAbsent(feedback.getTherapistId(), k -> new long[2]);
            acc[0] += feedback.getRatingStars();
            acc[1] += 1;
        }

        long maxRequests = requestCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long maxServices = serviceCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);

        List<Entry> entries = new ArrayList<>();
        for (Therapist therapist : therapistRepository.findAllByOrganizationIdAndActiveTrue(organizationId)) {
            UUID id = therapist.getId();
            long requests = requestCounts.getOrDefault(id, 0L);
            long services = serviceCounts.getOrDefault(id, 0L);
            long[] rating = ratingTotals.getOrDefault(id, new long[2]);
            double avgRating = rating[1] == 0 ? 0 : (double) rating[0] / rating[1];

            double normRating = avgRating / 5.0;
            double normRequests = maxRequests == 0 ? 0 : (double) requests / maxRequests;
            double normServices = maxServices == 0 ? 0 : (double) services / maxServices;
            double score = 0.5 * normRating + 0.25 * normRequests + 0.25 * normServices;

            entries.add(new Entry(id, therapist.getNickname(), round(avgRating), (int) rating[1],
                    requests, services, round(score)));
        }

        entries.sort(Comparator.comparingDouble(Entry::score).reversed());
        return new TopTherapistResponse(entries.stream().limit(10).toList());
    }

    private OffsetDateTime resolveFrom(Period period, OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(MANILA).toLocalDate();
        return switch (period) {
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(MANILA).toOffsetDateTime();
            case MONTHLY -> today.withDayOfMonth(1).atStartOfDay(MANILA).toOffsetDateTime();
            case PAYROLL -> {
                int dayOfMonth = today.getDayOfMonth();
                yield dayOfMonth <= 15
                        ? today.withDayOfMonth(1).atStartOfDay(MANILA).toOffsetDateTime()
                        : today.withDayOfMonth(16).atStartOfDay(MANILA).toOffsetDateTime();
            }
            case ALL -> EPOCH;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
