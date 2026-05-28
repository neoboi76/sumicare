package com.sumicare.voucher.controller;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.service.VoucherService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/vouchers")
public class PublicVoucherController {

    private final VoucherService voucherService;
    private final OrganizationRepository organizationRepository;

    public PublicVoucherController(VoucherService voucherService, OrganizationRepository organizationRepository) {
        this.voucherService = voucherService;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/{slug}/check")
    public PublicVoucherResponse check(@PathVariable String slug, @RequestParam String code) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        Voucher voucher = voucherService.findValid(organizationId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher invalid or already redeemed"));
        return new PublicVoucherResponse(voucher.getId(), voucher.getCode(), voucher.getName(),
                voucher.getDiscountAmount(), voucher.getDiscountPercent(), voucher.getTargetPackageId());
    }

    public record PublicVoucherResponse(UUID id, String code, String name, BigDecimal discountAmount,
                                        Integer discountPercent, Long targetPackageId) {}
}
