package com.sumicare.transaction.repository;

import com.sumicare.transaction.domain.TreatmentSlip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentSlipRepository extends JpaRepository<TreatmentSlip, UUID> {
    List<TreatmentSlip> findAllByOrganizationIdAndCreatedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    Optional<TreatmentSlip> findBySessionId(UUID sessionId);
    List<TreatmentSlip> findAllByBookingId(UUID bookingId);
}
