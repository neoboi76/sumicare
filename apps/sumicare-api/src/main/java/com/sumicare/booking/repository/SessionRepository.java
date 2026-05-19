package com.sumicare.booking.repository;

import com.sumicare.booking.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<Session> findAllByOrganizationIdAndStartedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<Session> findAllByStatusAndExpectedEndAtBefore(String status, OffsetDateTime cutoff);

    @Query("SELECT s.primaryTherapistId FROM Session s WHERE s.primaryTherapistId IN :ids AND s.status = 'ACTIVE' " +
           "UNION " +
           "SELECT s.secondaryTherapistId FROM Session s WHERE s.secondaryTherapistId IN :ids AND s.status = 'ACTIVE'")
    Set<UUID> findActiveTherapistIds(@Param("ids") Collection<UUID> ids);

    boolean existsByPrimaryTherapistIdAndStatus(UUID primaryTherapistId, String status);
    boolean existsBySecondaryTherapistIdAndStatus(UUID secondaryTherapistId, String status);
    List<Session> findAllByOrganizationIdAndStatus(UUID organizationId, String status);

    List<Session> findAllByOrganizationIdAndStatusAndExpectedEndAtBefore(
            UUID organizationId, String status, OffsetDateTime cutoff);

    long countByOrganizationIdAndStatus(UUID organizationId, String status);
}
