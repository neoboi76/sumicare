/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.audit.repository;

import com.sumicare.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);

    Page<AuditLog> findAllByOrganizationIdAndActorUserIdOrderByOccurredAtDesc(
            UUID organizationId, UUID actorUserId, Pageable pageable);

    Page<AuditLog> findAllByOrganizationIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID organizationId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findAllByOrganizationIdAndActorUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID organizationId, UUID actorUserId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    List<AuditLog> findAllByOrganizationIdAndTargetEntityAndTargetIdOrderByOccurredAtDesc(
            UUID organizationId, String targetEntity, String targetId);
}
