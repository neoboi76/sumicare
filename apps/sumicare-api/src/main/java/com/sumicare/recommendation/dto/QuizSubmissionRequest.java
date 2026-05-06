package com.sumicare.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record QuizSubmissionRequest(
        UUID clientId,
        List<QuizAnswer> answers
) {}
