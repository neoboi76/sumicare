/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.repository;

import com.sumicare.feedback.domain.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Page<Feedback> findAllByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId, Pageable pageable);
    List<Feedback> findTop20ByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId);
    List<Feedback> findAllByOrganizationIdAndSubmittedAtBetweenOrderBySubmittedAtAsc(UUID organizationId, OffsetDateTime from, OffsetDateTime to);

    long countByOrganizationIdAndReadAtIsNull(UUID organizationId);

    @Modifying
    @Query("update Feedback f set f.readAt = :readAt, f.readByUserId = :userId "
            + "where f.organizationId = :organizationId and f.readAt is null")
    int markAllRead(@Param("organizationId") UUID organizationId,
                    @Param("readAt") OffsetDateTime readAt,
                    @Param("userId") UUID userId);
}
