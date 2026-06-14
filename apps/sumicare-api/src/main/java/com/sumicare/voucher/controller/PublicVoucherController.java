package com.sumicare.voucher.controller;

import com.sumicare.client.domain.Client;
import com.sumicare.client.repository.ClientRepository;
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
    private final ClientRepository clientRepository;

    public PublicVoucherController(VoucherService voucherService,
                                   OrganizationRepository organizationRepository,
                                   ClientRepository clientRepository) {
        this.voucherService = voucherService;
        this.organizationRepository = organizationRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/{slug}/check")
    public PublicVoucherResponse check(@PathVariable String slug,
                                       @RequestParam String code,
                                       @RequestParam(required = false) String email) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        Voucher voucher = voucherService.findValid(organizationId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher is not valid or has expired."));
        UUID clientId = resolveClientId(organizationId, email);
        voucherService.assertRedeemableBy(organizationId, voucher.getId(), clientId);
        return new PublicVoucherResponse(voucher.getId(), voucher.getCode(), voucher.getName(),
                voucher.getDiscountAmount(), voucher.getDiscountPercent(), voucher.getTargetPackageId());
    }

    private UUID resolveClientId(UUID organizationId, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return clientRepository.findByOrganizationIdAndEmailAndDeletedAtIsNull(organizationId, email.trim())
                .map(Client::getId)
                .orElse(null);
    }

    public record PublicVoucherResponse(UUID id, String code, String name, BigDecimal discountAmount,
                                        Integer discountPercent, Long targetPackageId) {}
}
