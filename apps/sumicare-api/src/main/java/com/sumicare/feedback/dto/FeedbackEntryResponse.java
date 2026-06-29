/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.dto;

import java.util.Map;
import java.util.UUID;

public record FeedbackEntryResponse(
        UUID id,
        String feedbackType,
        UUID therapistId,
        String therapistNickname,
        int ratingStars,
        Integer npsScore,
        String comment,
        Map<String, Integer> criteria,
        String staffResponse,
        String submittedAt
) {}
