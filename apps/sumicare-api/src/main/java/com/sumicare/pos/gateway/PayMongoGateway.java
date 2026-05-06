package com.sumicare.pos.gateway;

import com.sumicare.common.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
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
    public String createIntent(BigDecimal amount, String currency, Map<String, String> metadata) {
        long amountInCentavos = amount.multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", amountInCentavos);
        attributes.put("currency", currency.toUpperCase());
        attributes.put("payment_method_allowed", new String[]{"gcash"});
        attributes.put("payment_method_options", Map.of("gcash", Map.of("redirect", Map.of(
                "success", appProperties.app().publicBaseUrl() + "/pay/success",
                "failed", appProperties.app().publicBaseUrl() + "/pay/failed"
        ))));
        attributes.put("description", metadata.getOrDefault("description", "SumiCare payment"));

        Map<String, Object> body = Map.of("data", Map.of("attributes", attributes));

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/payment_intents")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Map<?, ?> responseAttributes = (Map<?, ?>) data.get("attributes");
            Map<?, ?> nextAction = (Map<?, ?>) responseAttributes.get("next_action");
            if (nextAction != null) {
                Map<?, ?> redirect = (Map<?, ?>) nextAction.get("redirect");
                if (redirect != null) return (String) redirect.get("url");
            }
            return (String) data.get("id");
        } catch (Exception e) {
            throw new RuntimeException("PayMongo source creation failed: " + e.getMessage(), e);
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
}
