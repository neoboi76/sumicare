package com.sumicare.transaction.repository;

import com.sumicare.transaction.domain.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, Long> {
    List<Commission> findAllByOrganizationIdAndCreatedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    boolean existsBySessionIdAndTherapistId(UUID sessionId, UUID therapistId);
}
