/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.repository;

import com.sumicare.transaction.domain.TreatmentSlip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentSlipRepository extends JpaRepository<TreatmentSlip, UUID> {
    List<TreatmentSlip> findAllByOrganizationIdAndCreatedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);

    @Query("select s from TreatmentSlip s where s.organizationId = :organizationId "
            + "and coalesce(s.startTime, s.createdAt) between :from and :to "
            + "order by coalesce(s.startTime, s.createdAt) desc")
    List<TreatmentSlip> findAllByOrganizationIdAndScheduleBetween(@Param("organizationId") UUID organizationId,
                                                                  @Param("from") OffsetDateTime from,
                                                                  @Param("to") OffsetDateTime to);

    Optional<TreatmentSlip> findBySessionId(UUID sessionId);
    List<TreatmentSlip> findAllByBookingId(UUID bookingId);
}
