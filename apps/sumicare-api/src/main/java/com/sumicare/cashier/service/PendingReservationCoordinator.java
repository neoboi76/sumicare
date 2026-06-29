/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.service.BookingService;
import com.sumicare.cashier.dto.CreateOrderRequest;
import com.sumicare.cashier.dto.PaymentDetailsRequest;
import com.sumicare.cashier.dto.PendingPaymentResult;
import com.sumicare.pos.service.PayMongoService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class PendingReservationCoordinator {

    private final PendingReservationService pending;
    private final ObjectMapper mapper;
    private final OrderService orderService;
    private final BookingService bookingService;
    private final PayMongoService payMongoService;

    public PendingReservationCoordinator(PendingReservationService pending,
                                         ObjectMapper mapper,
                                         OrderService orderService,
                                         BookingService bookingService,
                                         PayMongoService payMongoService) {
        this.pending = pending;
        this.mapper = mapper;
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.payMongoService = payMongoService;
    }

    public PendingPaymentResult initiatePublicHold(UUID organizationId, CreateBookingRequest booking,
            String paymentMethod, BigDecimal amount, PaymentDetailsRequest details, String returnPath) {
        if (!bookingService.isHardPrepayRequired(booking.reservationType(), paymentMethod)) {
            throw new IllegalArgumentException("This reservation does not require online prepayment");
        }
        requirePositive(amount);
        String token = holdPublic(organizationId, booking, paymentMethod, amount, returnPath);
        return charge(token, amount, paymentMethod, details, returnPath);
    }

    public PendingPaymentResult initiateCashierHold(UUID organizationId, UUID cashierUserId, CreateOrderRequest order,
            String paymentMethod, BigDecimal amount, PaymentDetailsRequest details, String returnPath) {
        if (!PayMongoService.supports(paymentMethod)) {
            throw new IllegalArgumentException("This payment method does not require online prepayment");
        }
        requirePositive(amount);
        String token = holdCashier(organizationId, cashierUserId, order, paymentMethod, amount, returnPath);
        return charge(token, amount, paymentMethod, details, returnPath);
    }

    private PendingPaymentResult charge(String token, BigDecimal amount, String paymentMethod,
            PaymentDetailsRequest details, String returnPath) {
        try {
            PayMongoService.ChargeResult result = payMongoService.initiatePending(token, amount, paymentMethod, details, returnPath);
            if ("succeeded".equalsIgnoreCase(result.status())) {
                return finalizePaid(token, result.intentId(), paymentMethod, amount)
                        .orElseThrow(() -> new IllegalStateException("Reservation could not be finalized"));
            }
            return new PendingPaymentResult(result.status(), token, result.intentId(), result.nextActionUrl(),
                    null, null, null, null, null, null);
        } catch (RuntimeException e) {
            pending.release(token);
            throw e;
        }
    }

    public PendingPaymentResult confirm(String token, String intentId, String paymentMethod) {
        Optional<PendingPaymentResult> alreadyDone = readStoredResult(token);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }
        String status = payMongoService.confirm(intentId);
        if (!"succeeded".equalsIgnoreCase(status) && !"processing".equalsIgnoreCase(status)) {
            throw new IllegalStateException("PayMongo payment was not authorized (status: " + status + ")");
        }
        return finalizePaid(token, intentId, paymentMethod, null)
                .orElseThrow(() -> new IllegalStateException("This reservation has expired. Please book again."));
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A payable amount is required");
        }
    }

    public String holdPublic(UUID organizationId, CreateBookingRequest booking, String paymentMethod,
                             BigDecimal amount, String returnPath) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("kind", "PUBLIC");
        envelope.put("organizationId", organizationId.toString());
        envelope.put("paymentMethod", paymentMethod);
        envelope.put("amount", amount.toPlainString());
        if (returnPath != null) envelope.put("returnPath", returnPath);
        envelope.set("request", mapper.valueToTree(booking));
        return pending.hold(envelope.toString());
    }

    public String holdCashier(UUID organizationId, UUID cashierUserId, CreateOrderRequest order,
                              String paymentMethod, BigDecimal amount, String returnPath) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("kind", "CASHIER");
        envelope.put("organizationId", organizationId.toString());
        if (cashierUserId != null) envelope.put("cashierUserId", cashierUserId.toString());
        envelope.put("paymentMethod", paymentMethod);
        envelope.put("amount", amount.toPlainString());
        if (returnPath != null) envelope.put("returnPath", returnPath);
        envelope.set("request", mapper.valueToTree(order));
        return pending.hold(envelope.toString());
    }

    public Optional<PendingPaymentResult> finalizePaid(String token, String intentId, String paymentMethod, BigDecimal amount) {
        Optional<String> raw = pending.consume(token);
        if (raw.isEmpty()) {
            return readStoredResult(token);
        }
        try {
            JsonNode envelope = mapper.readTree(raw.get());
            UUID organizationId = UUID.fromString(envelope.path("organizationId").asText());
            String method = paymentMethod != null && !paymentMethod.isBlank()
                    ? paymentMethod : envelope.path("paymentMethod").asText();
            BigDecimal amt = amount != null ? amount : new BigDecimal(envelope.path("amount").asText("0"));

            PendingPaymentResult result;
            if ("CASHIER".equals(envelope.path("kind").asText())) {
                UUID cashierUserId = envelope.hasNonNull("cashierUserId")
                        ? UUID.fromString(envelope.get("cashierUserId").asText()) : null;
                CreateOrderRequest request = mapper.treeToValue(envelope.get("request"), CreateOrderRequest.class);
                result = orderService.createCashierFromPending(organizationId, cashierUserId, request, intentId, method, amt);
            } else {
                CreateBookingRequest request = mapper.treeToValue(envelope.get("request"), CreateBookingRequest.class);
                result = bookingService.createPublicFromPending(organizationId, request, intentId, method, amt);
            }
            pending.storeResult(token, mapper.writeValueAsString(result));
            return Optional.of(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to finalize the paid reservation: " + e.getMessage(), e);
        }
    }

    private Optional<PendingPaymentResult> readStoredResult(String token) {
        return pending.result(token).flatMap(json -> {
            try {
                return Optional.of(mapper.readValue(json, PendingPaymentResult.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }
}
