package com.sumicare.recommendation.repository;

import com.sumicare.recommendation.domain.RecommendationWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationWeightRepository extends JpaRepository<RecommendationWeight, Long> {
    List<RecommendationWeight> findAllByOrganizationId(UUID organizationId);
}
