/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.repository;

import com.sumicare.booking.domain.Session;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findFirstByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") UUID id);
    List<Session> findAllByOrganizationIdAndStartedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<Session> findAllByStatusAndExpectedEndAtBefore(String status, OffsetDateTime cutoff);

    @Query("SELECT s.primaryTherapistId FROM Session s WHERE s.organizationId = :organizationId AND s.primaryTherapistId IN :ids AND s.status = 'ACTIVE' " +
           "UNION " +
           "SELECT s.secondaryTherapistId FROM Session s WHERE s.organizationId = :organizationId AND s.secondaryTherapistId IN :ids AND s.status = 'ACTIVE'")
    Set<UUID> findActiveTherapistIds(@Param("organizationId") UUID organizationId, @Param("ids") Collection<UUID> ids);

    boolean existsByOrganizationIdAndPrimaryTherapistIdAndStatus(UUID organizationId, UUID primaryTherapistId, String status);
    boolean existsByOrganizationIdAndSecondaryTherapistIdAndStatus(UUID organizationId, UUID secondaryTherapistId, String status);
    List<Session> findAllByOrganizationIdAndStatus(UUID organizationId, String status);

    List<Session> findAllByOrganizationIdAndStatusAndExpectedEndAtBefore(
            UUID organizationId, String status, OffsetDateTime cutoff);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);

    long countByOrganizationIdAndStatusAndEndedAtBetween(
            UUID organizationId, String status, OffsetDateTime from, OffsetDateTime to);

    List<Session> findAllByBookingIdIn(Collection<UUID> bookingIds);
}
