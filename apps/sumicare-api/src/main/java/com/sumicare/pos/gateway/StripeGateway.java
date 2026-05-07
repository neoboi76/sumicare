package com.sumicare.pos.gateway;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.sumicare.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class StripeGateway implements PaymentGateway {

    private final AppProperties appProperties;

    public StripeGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
        Stripe.apiKey = appProperties.payment().stripe().secretKey();
    }

    @Override
    public String createIntent(BigDecimal amount, String currency, Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency(currency.toLowerCase())
                    .putAllMetadata(metadata)
                    .addPaymentMethodType("card")
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            return intent.getClientSecret();
        } catch (StripeException e) {
            throw new RuntimeException("Stripe intent creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        try {
            Webhook.constructEvent(payload, signature, appProperties.payment().stripe().webhookSecret());
            return true;
        } catch (SignatureVerificationException e) {
            return false;
        }
    }

    @Override
    public String capture(String intentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(intentId);
            if ("requires_capture".equals(intent.getStatus())) {
                intent.capture();
            }
            return intent.getStatus();
        } catch (StripeException e) {
            throw new RuntimeException("Stripe capture failed: " + e.getMessage(), e);
        }
    }
}
