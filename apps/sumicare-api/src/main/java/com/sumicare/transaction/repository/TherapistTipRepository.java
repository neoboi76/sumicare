/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.repository;

import com.sumicare.transaction.domain.TherapistTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TherapistTipRepository extends JpaRepository<TherapistTip, Long> {
    List<TherapistTip> findAllByOrganizationIdAndRecordedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<TherapistTip> findAllByOrganizationIdAndTherapistIdAndRecordedAtBetween(UUID organizationId, UUID therapistId, OffsetDateTime from, OffsetDateTime to);
    List<TherapistTip> findAllByOrderId(UUID orderId);
}
