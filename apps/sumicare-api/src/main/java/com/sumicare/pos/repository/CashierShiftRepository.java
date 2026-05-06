package com.sumicare.pos.repository;

import com.sumicare.pos.domain.CashierShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashierShiftRepository extends JpaRepository<CashierShift, UUID> {
    Optional<CashierShift> findFirstByCashierUserIdAndStatus(UUID cashierUserId, String status);
    List<CashierShift> findAllByOrganizationIdAndStatus(UUID organizationId, String status);
}
