/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.voucher.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.service.VoucherService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public List<Voucher> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return voucherService.list(UUID.fromString(principal.organizationId()));
    }

    @PostMapping
    public Voucher create(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody Voucher voucher) {
        return voucherService.create(UUID.fromString(principal.organizationId()), voucher);
    }

    @PutMapping("/{id}")
    public Voucher update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                          @PathVariable UUID id,
                          @RequestBody Voucher voucher) {
        return voucherService.update(UUID.fromString(principal.organizationId()), id, voucher);
    }

    @GetMapping("/check")
    public VoucherCheckResponse check(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestParam String code,
                                      @RequestParam BigDecimal subtotal,
                                      @RequestParam(required = false) UUID clientId) {
        UUID organizationId = UUID.fromString(principal.organizationId());
        Voucher voucher = voucherService.findValid(organizationId, code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher is not valid or has expired."));
        voucherService.assertRedeemableBy(organizationId, voucher.getId(), clientId);
        BigDecimal discount = voucherService.computeDiscount(voucher, subtotal);
        return new VoucherCheckResponse(voucher.getId(), voucher.getCode(), voucher.getName(), discount);
    }

    public record VoucherCheckResponse(UUID id, String code, String name, BigDecimal discount) {}
}
