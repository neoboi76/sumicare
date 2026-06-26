/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.pos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.service.OrderService;
import com.sumicare.cashier.service.PendingReservationCoordinator;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.gateway.PayMongoGateway;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PayMongoGateway payMongoGateway;
    private final TransactionLedgerRepository ledgerRepository;
    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PendingReservationCoordinator pendingCoordinator;
    private final ObjectMapper objectMapper;

    public WebhookController(PayMongoGateway payMongoGateway,
                             TransactionLedgerRepository ledgerRepository,
                             BookingRepository bookingRepository,
                             OrderRepository orderRepository,
                             OrderService orderService,
                             PendingReservationCoordinator pendingCoordinator,
                             ObjectMapper objectMapper) {
        this.payMongoGateway = payMongoGateway;
        this.ledgerRepository = ledgerRepository;
        this.bookingRepository = bookingRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.pendingCoordinator = pendingCoordinator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/paymongo")
    public ResponseEntity<String> paymongoWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Paymongo-Signature", required = false) String signature) {

        // Reject before touching any state: an absent or forged signature must never
        // reach the reconciliation logic that posts payments.
        if (signature == null || !payMongoGateway.verifyWebhook(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.at("/data/attributes/type").asText();
            if ("payment.paid".equals(type) || "source.chargeable".equals(type)) {
                JsonNode data = event.at("/data/attributes/data");
                BigDecimal amount = BigDecimal.valueOf(data.at("/attributes/amount").asLong(), 2);
                String gatewayId = data.path("id").asText("");
                JsonNode attributes = data.at("/attributes");
                String method = resolveMethod(attributes);
                String pendingToken = attributes.at("/metadata/pendingToken").asText("");
                if (!pendingToken.isBlank()) {
                    pendingCoordinator.finalizePaid(pendingToken, gatewayId, method, amount);
                } else {
                    String orderId = attributes.at("/metadata/orderId").asText("");
                    reconcile(orderId, amount, method, gatewayId);
                }
            } else if ("refund.updated".equals(type) || "payment.refunded".equals(type)) {
                JsonNode data = event.at("/data/attributes/data");
                JsonNode attributes = data.at("/attributes");
                String status = attributes.path("status").asText("");
                if (status.isBlank() || "succeeded".equalsIgnoreCase(status)) {
                    BigDecimal amount = BigDecimal.valueOf(attributes.path("amount").asLong(), 2);
                    String refundId = data.path("id").asText("");
                    String orderId = attributes.at("/metadata/orderId").asText("");
                    reconcileRefund(orderId, amount, refundId);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Processing error");
        }

        return ResponseEntity.ok("ok");
    }

    private void reconcile(String orderId, BigDecimal amount, String method, String gatewayId) {
        Order order = null;
        if (orderId != null && !orderId.isBlank()) {
            try {
                order = orderRepository.findById(UUID.fromString(orderId)).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Booking booking = null;
        if (order != null && order.getBookingId() != null) {
            booking = bookingRepository.findById(order.getBookingId()).orElse(null);
        }

        if (booking != null) {
            booking.setPaymentStatus("PAID");
            booking.setGatewayPaymentId(gatewayId);
            bookingRepository.save(booking);
        }

        if (order != null) {
            orderService.settleGatewayPayment(order, null, gatewayId, method, amount);
            return;
        }

        // No matching order: post a standalone ledger entry instead, but guard against
        // PayMongo redelivering the same event by skipping if this gateway id is already recorded.
        if (gatewayId != null && !gatewayId.isBlank() && ledgerRepository.existsByGatewayReference(gatewayId)) {
            return;
        }
        // Last-resort organization for an unmatched gateway event with no booking to
        // attribute it to; keeps the payment recorded rather than silently dropped.
        UUID orgId = booking != null ? booking.getOrganizationId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        TransactionLedgerEntry entry = new TransactionLedgerEntry();
        entry.setOrganizationId(orgId);
        entry.setTransactionId(UUID.randomUUID());
        entry.setEntryType("PAYMENT_RECEIVED");
        entry.setAmount(amount);
        entry.setPaymentMethod(method);
        entry.setRecordedAt(OffsetDateTime.now());
        entry.setGatewayReference(gatewayId);
        entry.setMetadata("{\"method\":\"" + method + "\",\"gatewayId\":\"" + gatewayId + "\"}");
        ledgerRepository.save(entry);
    }

    private void reconcileRefund(String orderId, BigDecimal amount, String refundId) {
        if (orderId == null || orderId.isBlank()) return;
        Order order;
        try {
            order = orderRepository.findById(UUID.fromString(orderId)).orElse(null);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (order == null) return;
        orderService.reconcileRefund(order, amount, refundId);
    }

    private String resolveMethod(JsonNode attributes) {
        String source = attributes.at("/source/type").asText("");
        if (source.isBlank()) {
            source = attributes.at("/payment_method/type").asText("");
        }
        return switch (source.toLowerCase()) {
            case "card" -> "CREDIT";
            case "gcash" -> "GCASH";
            default -> source.isBlank() ? "GCASH" : source.toUpperCase();
        };
    }

}
