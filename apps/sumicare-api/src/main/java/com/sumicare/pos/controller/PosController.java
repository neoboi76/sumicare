package com.sumicare.pos.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.pos.domain.CashierShift;
import com.sumicare.pos.dto.PaymentResponse;
import com.sumicare.pos.dto.ProcessPaymentRequest;
import com.sumicare.pos.repository.CashierShiftRepository;
import com.sumicare.pos.service.PosService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos")
public class PosController {

    private final PosService posService;
    private final CashierShiftRepository cashierShiftRepository;

    public PosController(PosService posService, CashierShiftRepository cashierShiftRepository) {
        this.posService = posService;
        this.cashierShiftRepository = cashierShiftRepository;
    }

    @PostMapping("/payments")
    public PaymentResponse processPayment(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @Valid @RequestBody ProcessPaymentRequest request) {
        return posService.processPayment(UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/cashier-shifts/open")
    public CashierShift openShift(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @RequestParam(required = false) BigDecimal openingFloat) {
        return posService.openCashierShift(UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), openingFloat);
    }

    @PostMapping("/cashier-shifts/{shiftId}/close")
    public CashierShift closeShift(@PathVariable UUID shiftId,
                                   @RequestParam(required = false) BigDecimal closingTotal) {
        return posService.closeCashierShift(shiftId, closingTotal);
    }

    @GetMapping("/cashier-shifts/mine")
    public List<CashierShift> myOpenShift(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return cashierShiftRepository
                .findFirstByCashierUserIdAndStatus(UUID.fromString(principal.userId()), "OPEN")
                .map(List::of)
                .orElseGet(List::of);
    }
}
