package com.sumicare.pos.gateway;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    IntentResult createIntent(BigDecimal amount, String currency, String paymentMethod, Map<String, String> metadata);

    boolean verifyWebhook(String payload, String signature);

    String capture(String intentId);

    record IntentResult(String intentId, String status, String nextActionUrl) {}
}
