/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.recommendation.controller;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.recommendation.dto.RecommendationRequest;
import com.sumicare.recommendation.dto.RecommendationResponse;
import com.sumicare.recommendation.service.RecommendationEngine;
import com.sumicare.service_catalogue.domain.Service;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RecommendationController {

    private static final String DEFAULT_DISCLAIMER =
            "SumiCare's recommendations are for relaxation purposes only and do not constitute medical advice.";

    private final RecommendationEngine engine;
    private final OrganizationRepository organizationRepository;

    public RecommendationController(RecommendationEngine engine,
                                    OrganizationRepository organizationRepository) {
        this.engine = engine;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/api/public/recommendation/{slug}")
    public RecommendationResponse recommend(@PathVariable String slug, @Valid @RequestBody RecommendationRequest request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        List<Service> ranked = engine.score(organizationId, request.answers());
        Service primary = ranked.isEmpty() ? null : ranked.get(0);
        List<Service> alternatives = ranked.size() > 1 ? ranked.subList(1, Math.min(ranked.size(), 3)) : List.of();
        return new RecommendationResponse(primary, alternatives, null, false, DEFAULT_DISCLAIMER);
    }
}
