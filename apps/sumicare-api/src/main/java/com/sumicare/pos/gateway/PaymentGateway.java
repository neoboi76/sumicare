package com.sumicare.pos.gateway;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    IntentResult createIntent(BigDecimal amount, String currency, String paymentMethod, Map<String, String> metadata);

    String createPaymentMethod(String paymentMethod, CardDetails card, Billing billing);

    IntentResult attachIntent(String intentId, String paymentMethodId, String clientKey, String returnUrl);

    IntentResult retrieveIntent(String intentId);

    String retrievePaymentId(String intentId);

    RefundResult refund(String paymentId, java.math.BigDecimal amount, String reason, String notes, Map<String, String> metadata);

    boolean verifyWebhook(String payload, String signature);

    String capture(String intentId);

    record IntentResult(String intentId, String status, String clientKey, String nextActionUrl) {}

    record CardDetails(String number, String expMonth, String expYear, String cvc) {}

    record Billing(String name, String email, String phone) {}

    record RefundResult(String refundId, String status, java.math.BigDecimal amount) {}
}
