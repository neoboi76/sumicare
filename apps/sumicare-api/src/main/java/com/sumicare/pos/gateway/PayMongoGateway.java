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
        Map<String, Object> methodOptions = new HashMap<>();
        for (String method : allowedMethods) {
            methodOptions.put(method, Map.of("redirect", Map.of(
                    "success", appProperties.app().publicBaseUrl() + "/pay/success",
                    "failed", appProperties.app().publicBaseUrl() + "/pay/failed"
            )));
        }
        attributes.put("payment_method_options", methodOptions);
        attributes.put("description", metadata.getOrDefault("description", "SumiCare payment"));
        attributes.put("metadata", new HashMap<>(metadata));

        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/payment_intents")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Map<?, ?> responseAttributes = (Map<?, ?>) data.get("attributes");
            String intentId = (String) data.get("id");
            String status = responseAttributes == null ? null : (String) responseAttributes.get("status");
            String nextActionUrl = null;
            if (responseAttributes != null) {
                Map<?, ?> nextAction = (Map<?, ?>) responseAttributes.get("next_action");
                if (nextAction != null) {
                    Map<?, ?> redirect = (Map<?, ?>) nextAction.get("redirect");
                    if (redirect != null) {
                        Object url = redirect.get("url");
                        if (url != null) nextActionUrl = url.toString();
                    }
                }
            }
            return new IntentResult(intentId, status == null ? "pending" : status, nextActionUrl);
        } catch (Exception e) {
            throw new RuntimeException("PayMongo intent creation failed: " + e.getMessage(), e);
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

    private List<String> resolveAllowedMethods(String paymentMethod) {
        if (paymentMethod == null) return List.of("gcash");
        return switch (paymentMethod.toUpperCase()) {
            case "CREDIT", "DEBIT" -> List.of("card");
            case "GCASH" -> List.of("gcash");
            default -> List.of("gcash");
        };
    }
}
