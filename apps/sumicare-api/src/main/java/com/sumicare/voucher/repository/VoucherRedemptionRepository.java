package com.sumicare.voucher.repository;

import com.sumicare.voucher.domain.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, UUID> {

    boolean existsByVoucherIdAndClientId(UUID voucherId, UUID clientId);

    long countByVoucherId(UUID voucherId);
}
