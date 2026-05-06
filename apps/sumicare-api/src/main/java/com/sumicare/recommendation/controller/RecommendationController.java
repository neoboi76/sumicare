package com.sumicare.recommendation.controller;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.recommendation.dto.QuizSubmissionRequest;
import com.sumicare.recommendation.dto.RecommendationResponse;
import com.sumicare.recommendation.service.RecommendationEngine;
import com.sumicare.recommendation.service.RecommendationExplainerService;
import com.sumicare.service_catalogue.domain.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RecommendationController {

    private final RecommendationEngine engine;
    private final RecommendationExplainerService explainer;
    private final OrganizationRepository organizationRepository;

    public RecommendationController(RecommendationEngine engine,
                                    RecommendationExplainerService explainer,
                                    OrganizationRepository organizationRepository) {
        this.engine = engine;
        this.explainer = explainer;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/api/public/recommendation/{slug}")
    public RecommendationResponse recommend(@PathVariable String slug, @RequestBody QuizSubmissionRequest request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        List<Service> ranked = engine.score(organizationId, request.answers());
        Service primary = ranked.isEmpty() ? null : ranked.get(0);
        List<Service> alternatives = ranked.size() > 1 ? ranked.subList(1, Math.min(ranked.size(), 3)) : List.of();
        String rationale = explainer.generateRationale(primary, request.answers());
        return new RecommendationResponse(primary, alternatives, rationale,
                rationale != null, explainer.disclaimer());
    }
}
