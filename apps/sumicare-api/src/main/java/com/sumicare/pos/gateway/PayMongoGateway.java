package com.sumicare.pos.gateway;

import com.sumicare.common.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PayMongoGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.paymongo.com/v1";

    private final AppProperties appProperties;
    private final RestClient restClient;

    public PayMongoGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
        String encoded = Base64.getEncoder().encodeToString(
                (appProperties.payment().paymongo().secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public IntentResult createIntent(BigDecimal amount, String currency, String paymentMethod, Map<String, String> metadata) {
        long amountInCentavos = amount.multiply(BigDecimal.valueOf(100)).longValue();
        List<String> allowedMethods = resolveAllowedMethods(paymentMethod);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", amountInCentavos);
        attributes.put("currency", currency.toUpperCase());
        attributes.put("payment_method_allowed", allowedMethods);
        attributes.put("capture_type", "automatic");
        attributes.put("description", metadata.getOrDefault("description", "SumiCare payment"));
        attributes.put("metadata", new HashMap<>(metadata));
        if (allowedMethods.contains("card")) {
            attributes.put("payment_method_options", Map.of(
                    "card", Map.of("request_three_d_secure", "any")));
        }

        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/payment_intents")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return readIntent(response);
        } catch (Exception e) {
            throw new RuntimeException("PayMongo intent creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String createPaymentMethod(String paymentMethod, CardDetails card, Billing billing) {
        Map<String, Object> attributes = new HashMap<>();
        String type = "GCASH".equalsIgnoreCase(paymentMethod) ? "gcash" : "card";
        attributes.put("type", type);
        if ("card".equals(type)) {
            if (card == null) {
                throw new IllegalArgumentException("Card details are required for card payments");
            }
            attributes.put("details", Map.of(
                    "card_number", card.number(),
                    "exp_month", Integer.parseInt(card.expMonth().trim()),
                    "exp_year", normaliseExpYear(card.expYear()),
                    "cvc", card.cvc()
            ));
        }
        Map<String, Object> billingMap = new HashMap<>();
        if (billing != null) {
            if (billing.name() != null && !billing.name().isBlank()) billingMap.put("name", billing.name());
            if (billing.email() != null && !billing.email().isBlank()) billingMap.put("email", billing.email());
            if (billing.phone() != null && !billing.phone().isBlank()) billingMap.put("phone", billing.phone());
        }
        if (!billingMap.isEmpty()) attributes.put("billing", billingMap);

        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/payment_methods")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            return (String) data.get("id");
        } catch (Exception e) {
            throw new RuntimeException("PayMongo payment method creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public IntentResult attachIntent(String intentId, String paymentMethodId, String clientKey, String returnUrl) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("payment_method", paymentMethodId);
        if (clientKey != null) attributes.put("client_key", clientKey);
        if (returnUrl != null) attributes.put("return_url", returnUrl);
        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/payment_intents/" + intentId + "/attach")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return readIntent(response);
        } catch (Exception e) {
            throw new RuntimeException("PayMongo intent attach failed: " + e.getMessage(), e);
        }
    }

    @Override
    public IntentResult retrieveIntent(String intentId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/payment_intents/" + intentId)
                    .retrieve()
                    .body(Map.class);
            return readIntent(response);
        } catch (Exception e) {
            throw new RuntimeException("PayMongo intent retrieval failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String retrievePaymentId(String intentId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/payment_intents/" + intentId)
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");
            Object payments = attributes == null ? null : attributes.get("payments");
            if (payments instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object id = first.get("id");
                return id == null ? null : id.toString();
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("PayMongo payment lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refund(String paymentId, BigDecimal amount, String reason, String notes, Map<String, String> metadata) {
        long amountInCentavos = amount.multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", amountInCentavos);
        attributes.put("payment_id", paymentId);
        attributes.put("reason", reason);
        if (notes != null && !notes.isBlank()) attributes.put("notes", notes);
        if (metadata != null && !metadata.isEmpty()) attributes.put("metadata", new HashMap<>(metadata));
        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/refunds")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Map<?, ?> refundAttributes = (Map<?, ?>) data.get("attributes");
            String refundId = (String) data.get("id");
            String status = refundAttributes == null ? null : (String) refundAttributes.get("status");
            return new RefundResult(refundId, status == null ? "pending" : status, amount);
        } catch (Exception e) {
            throw new RuntimeException("PayMongo refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        try {
            String webhookSecret = appProperties.payment().paymongo().webhookSecret();
            MessageDigest hmac = MessageDigest.getInstance("HmacSHA256");
            byte[] sigBytes = hmac.digest((webhookSecret + "." + payload).getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(sigBytes);
            return computed.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String capture(String intentId) {
        return intentId;
    }

    private IntentResult readIntent(Map<?, ?> response) {
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");
        String intentId = (String) data.get("id");
        String status = attributes == null ? null : (String) attributes.get("status");
        String clientKey = attributes == null ? null : (String) attributes.get("client_key");
        String nextActionUrl = null;
        if (attributes != null) {
            Map<?, ?> nextAction = (Map<?, ?>) attributes.get("next_action");
            if (nextAction != null) {
                Map<?, ?> redirect = (Map<?, ?>) nextAction.get("redirect");
                if (redirect != null) {
                    Object url = redirect.get("url");
                    if (url != null) nextActionUrl = url.toString();
                }
            }
        }
        return new IntentResult(intentId, status == null ? "pending" : status, clientKey, nextActionUrl);
    }

    private int normaliseExpYear(String expYear) {
        int year = Integer.parseInt(expYear.trim());
        return year < 100 ? 2000 + year : year;
    }

    private List<String> resolveAllowedMethods(String paymentMethod) {
        if (paymentMethod == null) return List.of("gcash");
        return switch (paymentMethod.toUpperCase()) {
            case "CREDIT", "DEBIT" -> List.of("card");
            case "GCASH" -> List.of("gcash");
            default -> List.of("gcash");
        };
    }
}
