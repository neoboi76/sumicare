package com.sumicare.pos.service;

import com.sumicare.cashier.domain.Order;
import com.sumicare.common.config.AppProperties;
import com.sumicare.pos.gateway.PayMongoGateway;
import com.sumicare.pos.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PayMongoService {

    private static final Logger log = LoggerFactory.getLogger(PayMongoService.class);

    private static final Set<String> SUPPORTED_METHODS = Set.of("CREDIT", "DEBIT", "GCASH");
    private static final Set<String> SUCCESS_STATUSES = Set.of("succeeded", "awaiting_next_action", "processing");

    private final PayMongoGateway gateway;
    private final AppProperties appProperties;

    public PayMongoService(PayMongoGateway gateway, AppProperties appProperties) {
        this.gateway = gateway;
        this.appProperties = appProperties;
    }

    public static boolean supports(String paymentMethod) {
        if (paymentMethod == null) return false;
        return SUPPORTED_METHODS.contains(paymentMethod.toUpperCase());
    }

    public ChargeResult charge(Order order, BigDecimal amount, String paymentMethod, String referenceNumber) {
        if (!supports(paymentMethod)) {
            throw new IllegalArgumentException("Unsupported PayMongo payment method: " + paymentMethod);
        }
        if (appProperties.payment().paymongo().mockMode()) {
            String intentId = "mock_pm_" + UUID.randomUUID();
            log.info("PayMongo mock charge: order={}, amount={}, method={}, intentId={}",
                    order.getId(), amount, paymentMethod, intentId);
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return new ChargeResult(intentId, "succeeded", null);
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", order.getId().toString());
        metadata.put("paymentMethod", paymentMethod);
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            metadata.put("reference", referenceNumber);
        }
        metadata.put("description", "Order " + order.getOrNumber() + " bookingId:" + order.getBookingId());

        PaymentGateway.IntentResult result = gateway.createIntent(amount, "PHP", paymentMethod, metadata);
        String status = result.status() == null ? "pending" : result.status();
        if (!SUCCESS_STATUSES.contains(status)) {
            throw new IllegalStateException("PayMongo intent created with non-success status: " + status);
        }
        return new ChargeResult(result.intentId(), status, result.nextActionUrl());
    }

    public record ChargeResult(String intentId, String status, String nextActionUrl) {}
}
