package com.sumicare.booking.repository;

import com.sumicare.booking.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findFirstByBookingId(UUID bookingId);
    List<Session> findAllByOrganizationIdAndStartedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<Session> findAllByStatusAndExpectedEndAtBefore(String status, OffsetDateTime cutoff);
}
