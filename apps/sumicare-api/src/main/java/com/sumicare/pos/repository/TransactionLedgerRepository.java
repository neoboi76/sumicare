package com.sumicare.pos.repository;

import com.sumicare.pos.domain.TransactionLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedgerEntry, Long> {
}
