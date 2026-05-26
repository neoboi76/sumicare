package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    List<LedgerAccount> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
