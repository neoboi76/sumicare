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
