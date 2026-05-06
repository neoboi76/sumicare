package com.sumicare.feedback.repository;

import com.sumicare.feedback.domain.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Page<Feedback> findAllByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId, Pageable pageable);
    List<Feedback> findTop20ByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId);
}
