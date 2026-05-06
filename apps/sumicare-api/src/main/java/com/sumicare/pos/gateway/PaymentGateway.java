package com.sumicare.pos.gateway;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    String createIntent(BigDecimal amount, String currency, Map<String, String> metadata);

    boolean verifyWebhook(String payload, String signature);

    String capture(String intentId);
}
