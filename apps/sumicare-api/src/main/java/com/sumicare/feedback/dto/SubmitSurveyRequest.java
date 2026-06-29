/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SubmitSurveyRequest(
        @Min(1) @Max(5) int lasemaRating,
        @Size(max = 2000) String lasemaComment,
        Map<String, Integer> lasemaCriteria,
        @Min(0) @Max(10) Integer npsScore,
        List<TherapistRating> therapists,
        List<TherapistTipEntry> tips
) {

    public record TherapistRating(
            UUID therapistId,
            @Min(1) @Max(5) int rating,
            @Size(max = 2000) String comment,
            Map<String, Integer> criteria
    ) {}

    public record TherapistTipEntry(
            UUID therapistId,
            BigDecimal amount
    ) {}
}
