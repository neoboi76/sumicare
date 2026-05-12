package com.sumicare.pos.repository;

import com.sumicare.pos.domain.PosTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {
    List<PosTransaction> findAllByOrganizationIdAndProcessedAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<PosTransaction> findAllByOrderId(UUID orderId);
}
