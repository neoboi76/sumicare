/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationRequest(
        UUID clientId,
        List<RecommendationAnswer> answers
) {}
