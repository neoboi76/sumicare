package com.sumicare.pos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.service.OrderService;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.gateway.PayMongoGateway;
import com.sumicare.pos.repository.TransactionLedgerRepository;
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
    private final ObjectMapper objectMapper;

    public WebhookController(PayMongoGateway payMongoGateway,
                             TransactionLedgerRepository ledgerRepository,
                             BookingRepository bookingRepository,
                             OrderRepository orderRepository,
                             OrderService orderService,
                             ObjectMapper objectMapper) {
        this.payMongoGateway = payMongoGateway;
        this.ledgerRepository = ledgerRepository;
        this.bookingRepository = bookingRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/paymongo")
    public ResponseEntity<String> paymongoWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Paymongo-Signature", required = false) String signature) {

        if (signature != null && !payMongoGateway.verifyWebhook(payload, signature)) {
            return ResponseEntity.badRequest().body("Invalid signature");
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
                String orderId = attributes.at("/metadata/orderId").asText("");
                String description = attributes.path("description").asText("");
                String bookingId = orderId.isBlank() ? extractBookingId(description) : null;
                reconcile(orderId, bookingId, amount, method, gatewayId);
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

    private void reconcile(String orderId, String bookingId, BigDecimal amount, String method, String gatewayId) {
        Order order = null;
        if (orderId != null && !orderId.isBlank()) {
            try {
                order = orderRepository.findById(UUID.fromString(orderId)).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Booking booking = null;
        if (order == null && bookingId != null && !bookingId.isBlank()) {
            try {
                UUID bid = UUID.fromString(bookingId);
                booking = bookingRepository.findById(bid).orElse(null);
                if (booking != null) {
                    order = orderRepository.findByBookingId(bid).orElse(null);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (order != null && order.getBookingId() != null) {
            booking = bookingRepository.findById(order.getBookingId()).orElse(booking);
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

        UUID orgId = booking != null ? booking.getOrganizationId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        TransactionLedgerEntry entry = new TransactionLedgerEntry();
        entry.setOrganizationId(orgId);
        entry.setTransactionId(UUID.randomUUID());
        entry.setEntryType("PAYMENT_RECEIVED");
        entry.setAmount(amount);
        entry.setPaymentMethod(method);
        entry.setRecordedAt(OffsetDateTime.now());
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

    private String extractBookingId(String description) {
        if (description == null) return null;
        String prefix = "bookingId:";
        int idx = description.indexOf(prefix);
        if (idx < 0) return null;
        String rest = description.substring(idx + prefix.length()).trim();
        int space = rest.indexOf(' ');
        return space > 0 ? rest.substring(0, space) : rest;
    }
}
