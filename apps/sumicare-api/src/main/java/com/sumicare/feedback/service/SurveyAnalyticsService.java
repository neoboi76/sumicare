/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SurveyAnalyticsService {

    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public SurveyAnalyticsService(FeedbackRepository feedbackRepository, ObjectMapper objectMapper) {
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
    }

    public record RatingDistribution(Map<Integer, Long> counts, double average, long total) {}

    public record NpsResult(int score, long promoters, long passives, long detractors, long respondents) {}

    public record LasemaSatisfactionStats(
            RatingDistribution overallDistribution,
            Map<String, Double> perCriterionAverages,
            double satisfactionIndex,
            NpsResult nps,
            List<String> recentComments
    ) {}

    public record TherapistSatisfactionStats(
            RatingDistribution overallDistribution,
            Map<String, Double> perCriterionAverages,
            double satisfactionIndex,
            List<String> recentComments
    ) {}

    public LasemaSatisfactionStats lasemaStats(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        List<Feedback> rows = feedbackRepository
                .findAllByOrganizationIdAndFeedbackTypeAndSubmittedAtBetween(organizationId, "LASEMA", from, to);

        RatingDistribution overall = computeDistribution(rows);
        Map<String, Double> criteria = computeCriteriaAverages(rows);
        double satisfactionIndex = computeSatisfactionIndex(overall.average(), criteria);
        NpsResult nps = computeNps(rows);
        List<String> comments = collectComments(rows);

        return new LasemaSatisfactionStats(overall, criteria, satisfactionIndex, nps, comments);
    }

    public TherapistSatisfactionStats therapistStats(UUID organizationId, UUID therapistId,
                                                     OffsetDateTime from, OffsetDateTime to) {
        List<Feedback> rows = feedbackRepository
                .findAllByOrganizationIdAndFeedbackTypeAndTherapistIdAndSubmittedAtBetween(
                        organizationId, "THERAPIST", therapistId, from, to);

        RatingDistribution overall = computeDistribution(rows);
        Map<String, Double> criteria = computeCriteriaAverages(rows);
        double satisfactionIndex = computeSatisfactionIndex(overall.average(), criteria);
        List<String> comments = collectComments(rows);

        return new TherapistSatisfactionStats(overall, criteria, satisfactionIndex, comments);
    }

    private RatingDistribution computeDistribution(List<Feedback> rows) {
        Map<Integer, Long> counts = new HashMap<>();
        for (int i = 1; i <= 5; i++) counts.put(i, 0L);
        long total = 0;
        long sum = 0;
        for (Feedback f : rows) {
            int r = f.getRatingStars();
            if (r >= 1 && r <= 5) {
                counts.merge(r, 1L, Long::sum);
                sum += r;
                total++;
            }
        }
        double average = total == 0 ? 0.0 : (double) sum / total;
        return new RatingDistribution(counts, average, total);
    }

    private Map<String, Double> computeCriteriaAverages(List<Feedback> rows) {
        Map<String, long[]> accumulator = new LinkedHashMap<>();
        for (Feedback f : rows) {
            if (f.getCriteria() == null) continue;
            try {
                Map<String, Integer> criteriaMap = objectMapper.readValue(
                        f.getCriteria(), new TypeReference<>() {});
                for (Map.Entry<String, Integer> e : criteriaMap.entrySet()) {
                    if (e.getValue() == null) continue;
                    long[] pair = accumulator.computeIfAbsent(e.getKey(), k -> new long[2]);
                    pair[0] += e.getValue();
                    pair[1]++;
                }
            } catch (Exception ignored) {}
        }
        Map<String, Double> averages = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : accumulator.entrySet()) {
            averages.put(e.getKey(), e.getValue()[1] == 0 ? 0.0 : (double) e.getValue()[0] / e.getValue()[1]);
        }
        return averages;
    }

    private double computeSatisfactionIndex(double overallAverage, Map<String, Double> criteria) {
        if (criteria.isEmpty()) return overallAverage / 5.0 * 100.0;
        double criteriaSum = criteria.values().stream().mapToDouble(Double::doubleValue).sum();
        double criteriaAvg = criteriaSum / criteria.size();
        double combined = (overallAverage + criteriaAvg) / 2.0;
        return Math.round(combined / 5.0 * 100.0 * 10.0) / 10.0;
    }

    private NpsResult computeNps(List<Feedback> rows) {
        long promoters = 0;
        long passives = 0;
        long detractors = 0;
        long respondents = 0;
        for (Feedback f : rows) {
            if (f.getNpsScore() == null) continue;
            int score = f.getNpsScore();
            respondents++;
            if (score >= 9) promoters++;
            else if (score >= 7) passives++;
            else detractors++;
        }
        int nps = respondents == 0 ? 0
                : (int) Math.round(((double) promoters - detractors) / respondents * 100.0);
        return new NpsResult(nps, promoters, passives, detractors, respondents);
    }

    private List<String> collectComments(List<Feedback> rows) {
        List<String> comments = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0 && comments.size() < 10; i--) {
            String comment = rows.get(i).getComment();
            if (comment != null && !comment.isBlank()) {
                comments.add(comment.trim());
            }
        }
        return comments;
    }
}
