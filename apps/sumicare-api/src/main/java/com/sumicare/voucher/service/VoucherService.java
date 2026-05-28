package com.sumicare.voucher.service;

import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.repository.VoucherRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoucherService {

    private final VoucherRepository repository;

    public VoucherService(VoucherRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<Voucher> list(UUID organizationId) {
        return repository.findAllByOrganizationId(organizationId);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Voucher create(UUID organizationId, Voucher v) {
        v.setOrganizationId(organizationId);
        v.setActive(true);
        v.setRedeemedAt(null);
        v.setRedeemedByClientId(null);
        return repository.save(v);
    }

    public Optional<Voucher> findValid(UUID organizationId, String code) {
        return repository.findByOrganizationIdAndCode(organizationId, code)
                .filter(Voucher::isActive)
                .filter(v -> v.getRedeemedAt() == null)
                .filter(v -> v.getValidFrom() == null || !LocalDate.now().isBefore(v.getValidFrom()))
                .filter(v -> v.getValidUntil() == null || !LocalDate.now().isAfter(v.getValidUntil()));
    }

    public BigDecimal computeDiscount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getDiscountAmount() != null) {
            return voucher.getDiscountAmount().min(subtotal);
        }
        if (voucher.getDiscountPercent() != null) {
            BigDecimal pct = BigDecimal.valueOf(voucher.getDiscountPercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return subtotal.multiply(pct).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public void markRedeemed(UUID voucherId, UUID clientId) {
        Voucher v = repository.findById(voucherId).orElseThrow();
        v.setRedeemedAt(OffsetDateTime.now());
        v.setRedeemedByClientId(clientId);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Voucher update(UUID organizationId, UUID id, Voucher updates) {
        Voucher v = repository.findById(id)
                .filter(existing -> existing.getOrganizationId().equals(organizationId))
                .orElseThrow();
        if (updates.getCode() != null && !updates.getCode().isBlank()) v.setCode(updates.getCode());
        if (updates.getName() != null) v.setName(updates.getName());
        if (updates.getDiscountAmount() != null) v.setDiscountAmount(updates.getDiscountAmount());
        if (updates.getDiscountPercent() != null) v.setDiscountPercent(updates.getDiscountPercent());
        if (updates.getValidFrom() != null) v.setValidFrom(updates.getValidFrom());
        if (updates.getValidUntil() != null) v.setValidUntil(updates.getValidUntil());
        if (updates.isActive() != v.isActive()) v.setActive(updates.isActive());
        v.setTargetPackageId(updates.getTargetPackageId());
        return repository.save(v);
    }
}
