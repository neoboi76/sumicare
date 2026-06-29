/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.repository;

import com.sumicare.transaction.domain.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, Long> {
    List<Commission> findAllByOrganizationIdAndCreatedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    boolean existsBySessionIdAndTherapistId(UUID sessionId, UUID therapistId);
    boolean existsBySessionIdAndTherapistIdAndExtensionFalse(UUID sessionId, UUID therapistId);
}
