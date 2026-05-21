package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.dto.CreateOrderRequest;
import com.sumicare.cashier.dto.OrderResponse;
import com.sumicare.cashier.dto.PayMongoConfirmRequest;
import com.sumicare.cashier.dto.PayMongoInitiateResponse;
import com.sumicare.cashier.dto.RecordPaymentRequest;
import com.sumicare.cashier.dto.RefundRequest;
import com.sumicare.cashier.service.OrderService;
import com.sumicare.print.ReceiptPdfService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier/orders")
public class OrderController {

    private final OrderService orderService;
    private final ReceiptPdfService receiptPdfService;

    public OrderController(OrderService orderService, ReceiptPdfService receiptPdfService) {
        this.orderService = orderService;
        this.receiptPdfService = receiptPdfService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(UUID.fromString(principal.organizationId()),
                UUID.fromString(principal.userId()), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @PathVariable UUID id,
                                @Valid @RequestBody CreateOrderRequest request) {
        return orderService.update(UUID.fromString(principal.organizationId()), id,
                UUID.fromString(principal.userId()), request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<OrderResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestParam(required = false) String status) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<String> statuses = (status == null || status.isBlank())
                ? List.of()
                : Arrays.stream(status.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return orderService.list(orgId, statuses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                              @PathVariable UUID id) {
        return orderService.get(UUID.fromString(principal.organizationId()), id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse recordPayment(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody RecordPaymentRequest request) {
        return orderService.recordPayment(UUID.fromString(principal.organizationId()), id,
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/{id}/paymongo/initiate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public PayMongoInitiateResponse initiatePayMongo(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody RecordPaymentRequest request) {
        return orderService.initiatePayMongo(UUID.fromString(principal.organizationId()), id,
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/{id}/paymongo/confirm")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse confirmPayMongo(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody PayMongoConfirmRequest request) {
        return orderService.confirmPayMongo(UUID.fromString(principal.organizationId()), id,
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public OrderResponse refund(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @PathVariable UUID id,
                                @Valid @RequestBody RefundRequest request) {
        return orderService.refundOrder(UUID.fromString(principal.organizationId()), id,
                UUID.fromString(principal.userId()), request);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse markPaid(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id) {
        return orderService.markPaid(UUID.fromString(principal.organizationId()), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @PathVariable UUID id,
                                @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return orderService.cancel(UUID.fromString(principal.organizationId()), id, reason);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse openOrder(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                   @PathVariable UUID id) {
        return orderService.openOrder(UUID.fromString(principal.organizationId()), id);
    }

    @PostMapping("/{id}/cancel-payment")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse cancelPayment(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @PathVariable UUID id) {
        return orderService.cancelPayment(UUID.fromString(principal.organizationId()), id);
    }

    @GetMapping("/by-booking/{bookingId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public OrderResponse byBooking(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                   @PathVariable UUID bookingId) {
        return orderService.getByBookingId(UUID.fromString(principal.organizationId()), bookingId);
    }

    @GetMapping("/{id}/receipt.pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ResponseEntity<byte[]> receiptPdf(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                              @PathVariable UUID id) {
        orderService.get(UUID.fromString(principal.organizationId()), id);
        byte[] data = receiptPdfService.renderReceipt(id);
        String filename = "receipt-" + id + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
