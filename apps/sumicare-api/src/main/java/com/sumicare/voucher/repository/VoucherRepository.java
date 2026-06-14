package com.sumicare.voucher.repository;

import com.sumicare.voucher.domain.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    Optional<Voucher> findByOrganizationIdAndCode(UUID organizationId, String code);
    List<Voucher> findAllByOrganizationId(UUID organizationId);
}
