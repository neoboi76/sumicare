package com.sumicare.pos.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.pos.domain.CashierShift;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.dto.PaymentResponse;
import com.sumicare.pos.dto.ProcessPaymentRequest;
import com.sumicare.pos.gateway.StripeGateway;
import com.sumicare.pos.repository.CashierShiftRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import com.sumicare.pos.service.PosService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier")
public class PosController {

    private final PosService posService;
    private final CashierShiftRepository cashierShiftRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final StripeGateway stripeGateway;

    public PosController(PosService posService, CashierShiftRepository cashierShiftRepository,
                         TransactionLedgerRepository ledgerRepository, StripeGateway stripeGateway) {
        this.posService = posService;
        this.cashierShiftRepository = cashierShiftRepository;
        this.ledgerRepository = ledgerRepository;
        this.stripeGateway = stripeGateway;
    }

    @PostMapping("/payments")
    public PaymentResponse processPayment(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @Valid @RequestBody ProcessPaymentRequest request) {
        return posService.processPayment(UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/payment-intent")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> createPaymentIntent(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String currency = body.getOrDefault("currency", "PHP").toString();
        String bookingId = body.getOrDefault("bookingId", "").toString();
        String clientSecret = stripeGateway.createIntent(amount, currency, Map.of("bookingId", bookingId));
        return Map.of("clientSecret", clientSecret);
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
