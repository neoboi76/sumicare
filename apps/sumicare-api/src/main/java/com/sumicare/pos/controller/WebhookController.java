package com.sumicare.pos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
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
    private final ObjectMapper objectMapper;

    public WebhookController(PayMongoGateway payMongoGateway,
                             TransactionLedgerRepository ledgerRepository,
                             BookingRepository bookingRepository,
                             ObjectMapper objectMapper) {
        this.payMongoGateway = payMongoGateway;
        this.ledgerRepository = ledgerRepository;
        this.bookingRepository = bookingRepository;
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
                String sourceId = data.get("id").asText();
                String description = data.at("/attributes/description").asText("");
                String bookingId = extractBookingId(description);
                recordPayment(bookingId, amount, "GCASH", sourceId);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Processing error");
        }

        return ResponseEntity.ok("ok");
    }

    private void recordPayment(String bookingId, BigDecimal amount, String method, String gatewayId) {
        if (bookingId == null || bookingId.isBlank()) return;

        try {
            UUID bid = UUID.fromString(bookingId);
            Optional<Booking> booking = bookingRepository.findById(bid);
            UUID orgId = booking.map(Booking::getOrganizationId).orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));

            booking.ifPresent(b -> {
                b.setPaymentStatus("PAID");
                b.setGatewayPaymentId(gatewayId);
                bookingRepository.save(b);
            });

            TransactionLedgerEntry entry = new TransactionLedgerEntry();
            entry.setOrganizationId(orgId);
            entry.setTransactionId(UUID.randomUUID());
            entry.setEntryType("PAYMENT_RECEIVED");
            entry.setAmount(amount);
            entry.setRecordedAt(OffsetDateTime.now());
            entry.setMetadata("{\"method\":\"" + method + "\",\"gatewayId\":\"" + gatewayId + "\",\"bookingId\":\"" + bookingId + "\"}");
            ledgerRepository.save(entry);
        } catch (Exception ignored) {
        }
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
