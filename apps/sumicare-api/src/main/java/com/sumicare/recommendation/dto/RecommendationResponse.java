/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.recommendation.dto;

import com.sumicare.service_catalogue.domain.Service;

import java.util.List;

public record RecommendationResponse(
        Service primary,
        List<Service> alternatives,
        String rationale,
        boolean aiUsed,
        String disclaimer
) {}
