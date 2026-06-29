/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.voucher.service;

import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.domain.VoucherRedemption;
import com.sumicare.voucher.repository.VoucherRedemptionRepository;
import com.sumicare.voucher.repository.VoucherRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VoucherService {

    private final VoucherRepository repository;
    private final VoucherRedemptionRepository redemptionRepository;

    public VoucherService(VoucherRepository repository, VoucherRedemptionRepository redemptionRepository) {
        this.repository = repository;
        this.redemptionRepository = redemptionRepository;
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
        return repository.save(v);
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
        v.setUsageLimit(updates.getUsageLimit());
        if (updates.isActive() != v.isActive()) v.setActive(updates.isActive());
        v.setTargetPackageId(updates.getTargetPackageId());
        if (updates.getApplicability() != null) v.setApplicability(updates.getApplicability());
        v.setEligibleClientIds(updates.getEligibleClientIds() == null
                ? new java.util.HashSet<>() : updates.getEligibleClientIds());
        return repository.save(v);
    }

    public List<Voucher> eligibleForClient(UUID organizationId, UUID clientId) {
        LocalDate today = LocalDate.now();
        return repository.findAllByOrganizationId(organizationId).stream()
                .filter(Voucher::isActive)
                .filter(v -> v.getValidFrom() == null || !today.isBefore(v.getValidFrom()))
                .filter(v -> v.getValidUntil() == null || !today.isAfter(v.getValidUntil()))
                .filter(v -> "ALL".equalsIgnoreCase(v.getApplicability())
                        || (clientId != null && v.getEligibleClientIds().contains(clientId)))
                .filter(v -> isRedeemableBy(v, clientId))
                .toList();
    }

    public Optional<Voucher> findValid(UUID organizationId, String code) {
        return repository.findByOrganizationIdAndCode(organizationId, code)
                .filter(Voucher::isActive)
                .filter(v -> v.getValidFrom() == null || !LocalDate.now().isBefore(v.getValidFrom()))
                .filter(v -> v.getValidUntil() == null || !LocalDate.now().isAfter(v.getValidUntil()));
    }

    public boolean isRedeemableBy(Voucher voucher, UUID clientId) {
        if ("SPECIFIC".equalsIgnoreCase(voucher.getApplicability())
                && (clientId == null || !voucher.getEligibleClientIds().contains(clientId))) {
            return false;
        }
        if (voucher.getUsageLimit() != null
                && redemptionRepository.countByVoucherId(voucher.getId()) >= voucher.getUsageLimit()) {
            return false;
        }
        return clientId == null || !redemptionRepository.existsByVoucherIdAndClientId(voucher.getId(), clientId);
    }

    public void assertRedeemableBy(UUID organizationId, UUID voucherId, UUID clientId) {
        if (voucherId == null) {
            return;
        }
        Voucher voucher = repository.findById(voucherId)
                .filter(v -> organizationId.equals(v.getOrganizationId()))
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found"));
        if ("SPECIFIC".equalsIgnoreCase(voucher.getApplicability())
                && (clientId == null || !voucher.getEligibleClientIds().contains(clientId))) {
            throw new IllegalStateException("This voucher is not applicable to this client.");
        }
        if (clientId != null && redemptionRepository.existsByVoucherIdAndClientId(voucherId, clientId)) {
            throw new IllegalStateException("This client has already used this voucher.");
        }
        if (voucher.getUsageLimit() != null
                && redemptionRepository.countByVoucherId(voucherId) >= voucher.getUsageLimit()) {
            throw new IllegalStateException("This voucher has reached its usage limit.");
        }
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

    public BigDecimal discountForVoucher(UUID organizationId, UUID voucherId, BigDecimal subtotal) {
        if (voucherId == null) {
            return BigDecimal.ZERO;
        }
        return repository.findById(voucherId)
                .filter(v -> organizationId.equals(v.getOrganizationId()))
                .map(v -> computeDiscount(v, subtotal == null ? BigDecimal.ZERO : subtotal))
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void markRedeemed(UUID voucherId, UUID clientId, UUID orderId) {
        if (voucherId == null || clientId == null) {
            return;
        }
        if (redemptionRepository.existsByVoucherIdAndClientId(voucherId, clientId)) {
            return;
        }
        Voucher voucher = repository.findById(voucherId).orElseThrow();
        VoucherRedemption redemption = new VoucherRedemption();
        redemption.setOrganizationId(voucher.getOrganizationId());
        redemption.setVoucherId(voucherId);
        redemption.setClientId(clientId);
        redemption.setOrderId(orderId);
        redemptionRepository.save(redemption);
    }
}
