/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.recommendation.service;

import com.sumicare.recommendation.domain.RecommendationWeight;
import com.sumicare.recommendation.dto.RecommendationAnswer;
import com.sumicare.recommendation.repository.RecommendationWeightRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class RecommendationEngine {

    private final RecommendationWeightRepository weightRepository;
    private final ServiceRepository serviceRepository;

    public RecommendationEngine(RecommendationWeightRepository weightRepository, ServiceRepository serviceRepository) {
        this.weightRepository = weightRepository;
        this.serviceRepository = serviceRepository;
    }

    public List<Service> score(UUID organizationId, List<RecommendationAnswer> answers) {
        List<RecommendationWeight> weights = weightRepository.findAllByOrganizationId(organizationId);
        Map<String, Map<String, List<RecommendationWeight>>> indexed = weights.stream()
                .collect(Collectors.groupingBy(RecommendationWeight::getQuestionCode,
                        Collectors.groupingBy(RecommendationWeight::getOptionCode)));
        Map<Long, Integer> totals = new HashMap<>();
        for (RecommendationAnswer answer : answers) {
            List<RecommendationWeight> matching = indexed
                    .getOrDefault(answer.questionCode(), Map.of())
                    .getOrDefault(answer.optionCode(), List.of());
            for (RecommendationWeight w : matching) {
                totals.merge(w.getServiceId(), w.getWeight(), Integer::sum);
            }
        }
        List<Service> services = serviceRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
        return services.stream()
                .filter(s -> totals.containsKey(s.getId()))
                .sorted(Comparator.comparingInt((Service s) -> totals.getOrDefault(s.getId(), 0)).reversed())
                .toList();
    }
}
