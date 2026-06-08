package com.sumicare.pos.repository;

import com.sumicare.pos.domain.TransactionLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedgerEntry, Long> {

    List<TransactionLedgerEntry> findAllByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID organizationId, OffsetDateTime from, OffsetDateTime to);

    List<TransactionLedgerEntry> findAllByOrganizationIdAndEntryTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID organizationId, String entryType, OffsetDateTime from, OffsetDateTime to);

    List<TransactionLedgerEntry> findAllByOrganizationIdAndPaymentMethodAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID organizationId, String paymentMethod, OffsetDateTime from, OffsetDateTime to);

    boolean existsByGatewayReference(String gatewayReference);
}
