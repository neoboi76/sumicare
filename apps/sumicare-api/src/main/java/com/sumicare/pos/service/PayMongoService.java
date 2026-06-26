/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.pos.service;

import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.dto.PaymentDetailsRequest;
import com.sumicare.common.config.AppProperties;
import com.sumicare.pos.gateway.PayMongoGateway;
import com.sumicare.pos.gateway.PaymentGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PayMongoService {

    private static final Logger log = LoggerFactory.getLogger(PayMongoService.class);

    private static final Set<String> SUPPORTED_METHODS = Set.of("CREDIT", "DEBIT", "GCASH");
    private static final Set<String> SUCCESS_STATUSES = Set.of("succeeded", "awaiting_next_action", "processing");
    private static final String MOCK_PREFIX = "mock_pi_";
    private static final String CHECKOUT_PREFIX = "cs_";

    private static final Set<String> TEST_CARD_DECLINES = Set.of(
            "4571736000000075", "5100000000000511", "4200000000000018");

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
        return charge(order, amount, paymentMethod, referenceNumber, null);
    }

    public ChargeResult charge(Order order, BigDecimal amount, String paymentMethod,
                               String referenceNumber, PaymentDetailsRequest details) {
        return runCharge(contextForOrder(order), amount, paymentMethod, referenceNumber, details, false, "/sumicare/app/cashier");
    }

    public ChargeResult initiate(Order order, BigDecimal amount, String paymentMethod,
                                 String referenceNumber, PaymentDetailsRequest details, String returnPath) {
        return runCharge(contextForOrder(order), amount, paymentMethod, referenceNumber, details, true, returnPath);
    }

    public ChargeResult initiatePending(String pendingToken, BigDecimal amount, String paymentMethod,
                                        PaymentDetailsRequest details, String returnPath) {
        return runCharge(contextForPending(pendingToken), amount, paymentMethod, null, details, true, returnPath);
    }

    private ChargeContext contextForOrder(Order order) {
        return new ChargeContext("orderId", order.getId().toString(),
                "Order " + order.getOrNumber() + " bookingId:" + order.getBookingId());
    }

    private ChargeContext contextForPending(String pendingToken) {
        return new ChargeContext("pendingToken", pendingToken, "Pending reservation " + pendingToken);
    }

    private record ChargeContext(String idParam, String idValue, String description) {}

    public String confirm(String intentId) {
        if (intentId == null) {
            throw new IllegalArgumentException("Missing payment reference");
        }
        if (appProperties.payment().paymongo().mockMode() || intentId.startsWith(MOCK_PREFIX)) {
            return "succeeded";
        }
        if (intentId.startsWith(CHECKOUT_PREFIX)) {
            PaymentGateway.CheckoutResult session = gateway.retrieveCheckoutSession(intentId);
            return "paid".equalsIgnoreCase(session.status()) ? "succeeded" : session.status();
        }
        PaymentGateway.IntentResult intent = gateway.retrieveIntent(intentId);
        return intent.status() == null ? "pending" : intent.status();
    }

    public RefundResult refund(String intentId, BigDecimal amount, String reason, String notes, String orderId) {
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("Missing payment reference for refund");
        }
        String normalisedReason = normaliseReason(reason);
        if (appProperties.payment().paymongo().mockMode() || intentId.startsWith(MOCK_PREFIX)) {
            String refundId = "mock_rf_" + UUID.randomUUID();
            log.info("PayMongo mock refund: intent={}, amount={}, reason={}, refundId={}",
                    intentId, amount, normalisedReason, refundId);
            sleepBriefly();
            return new RefundResult(refundId, "succeeded", amount);
        }
        String paymentId = gateway.retrievePaymentId(intentId);
        if (paymentId == null) {
            throw new IllegalStateException("No PayMongo payment found for intent " + intentId);
        }
        Map<String, String> metadata = new HashMap<>();
        if (orderId != null && !orderId.isBlank()) metadata.put("orderId", orderId);
        PaymentGateway.RefundResult result = gateway.refund(paymentId, amount, normalisedReason, notes, metadata);
        return new RefundResult(result.refundId(), result.status(), result.amount());
    }

    private String normaliseReason(String reason) {
        if (reason == null) return "requested_by_customer";
        return switch (reason.toLowerCase()) {
            case "duplicate", "fraudulent", "requested_by_customer", "others" -> reason.toLowerCase();
            default -> "others";
        };
    }

    private ChargeResult runCharge(ChargeContext ctx, BigDecimal amount, String paymentMethod,
                                   String referenceNumber, PaymentDetailsRequest details, boolean redirect, String returnPath) {
        if (!supports(paymentMethod)) {
            throw new IllegalArgumentException("Unsupported PayMongo payment method: " + paymentMethod);
        }
        boolean card = !"GCASH".equalsIgnoreCase(paymentMethod);

        if (appProperties.payment().paymongo().mockMode()) {
            return mockCharge(ctx, amount, paymentMethod, card, details, redirect);
        }

        if (card) {
            return cardCheckout(ctx, amount, paymentMethod, returnPath);
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ctx.idParam(), ctx.idValue());
        metadata.put("paymentMethod", paymentMethod);
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            metadata.put("reference", referenceNumber);
        }
        metadata.put("description", ctx.description());

        if (!card) {
            if (details == null || details.gcashPhone() == null || details.gcashPhone().isBlank()) {
                throw new IllegalArgumentException("A GCash mobile number is required");
            }
            if (details.gcashEmail() == null || details.gcashEmail().isBlank()) {
                throw new IllegalArgumentException("A GCash email is required");
            }
        }
        PaymentGateway.IntentResult intent = gateway.createIntent(amount, "PHP", paymentMethod, metadata);
        PaymentGateway.CardDetails cardDetails = card ? toCardDetails(details) : null;
        PaymentGateway.Billing billing = toBilling(card, details);
        String paymentMethodId = gateway.createPaymentMethod(paymentMethod, cardDetails, billing);
        String returnUrl = buildReturnUrl(ctx, intent.intentId(), paymentMethod, amount, returnPath);
        PaymentGateway.IntentResult attached = gateway.attachIntent(
                intent.intentId(), paymentMethodId, intent.clientKey(), returnUrl);

        String status = attached.status() == null ? "pending" : attached.status();
        if (!SUCCESS_STATUSES.contains(status)) {
            throw new IllegalStateException("PayMongo charge did not succeed: " + status);
        }
        return new ChargeResult(attached.intentId(), status, attached.nextActionUrl());
    }

    private ChargeResult cardCheckout(ChargeContext ctx, BigDecimal amount, String paymentMethod, String returnPath) {
        String successUrl = buildReturnUrl(ctx, null, paymentMethod, amount, returnPath);
        // Cancel returns to the same page; the trailing status flag lets the frontend
        // distinguish an abandoned checkout from a successful return.
        String cancelUrl = successUrl + "&status=cancelled";
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ctx.idParam(), ctx.idValue());
        metadata.put("paymentMethod", paymentMethod);
        PaymentGateway.CheckoutResult session = gateway.createCheckoutSession(
                amount, "PHP", java.util.List.of("card"), ctx.description(), successUrl, cancelUrl, metadata);
        if (session.checkoutUrl() == null || session.checkoutUrl().isBlank()) {
            throw new IllegalStateException("PayMongo did not return a checkout URL");
        }
        return new ChargeResult(session.sessionId(), "awaiting_next_action", session.checkoutUrl());
    }

    private ChargeResult mockCharge(ChargeContext ctx, BigDecimal amount, String paymentMethod,
                                    boolean card, PaymentDetailsRequest details, boolean redirect) {
        String intentId = MOCK_PREFIX + UUID.randomUUID();
        if (card) {
            String number = details == null || details.cardNumber() == null
                    ? "" : details.cardNumber().replaceAll("\\s+", "");
            if (!number.isBlank() && TEST_CARD_DECLINES.contains(number)) {
                throw new IllegalStateException("PayMongo declined the card (test decline number)");
            }
            log.info("PayMongo mock card charge: ref={}, amount={}, method={}, intentId={}, redirect={}",
                    ctx.idValue(), amount, paymentMethod, intentId, redirect);
            sleepBriefly();
            if (redirect) {
                return new ChargeResult(intentId, "awaiting_next_action", null);
            }
            return new ChargeResult(intentId, "succeeded", null);
        }
        if (details == null || details.gcashPhone() == null || details.gcashPhone().isBlank()) {
            throw new IllegalArgumentException("A GCash mobile number is required");
        }
        log.info("PayMongo mock GCash charge: ref={}, amount={}, intentId={}, redirect={}",
                ctx.idValue(), amount, intentId, redirect);
        sleepBriefly();
        return new ChargeResult(intentId, "awaiting_next_action", null);
    }

    // Builds the URL PayMongo redirects the payer back to after the hosted checkout or
    // 3DS step. The order, intent, method, and amount are carried as query params so the
    // cashier (or public order) page can re-identify the order and confirm it on return.
    private String buildReturnUrl(ChargeContext ctx, String intentId, String paymentMethod, BigDecimal amount, String returnPath) {
        String base;
        String path;
        if (isAbsoluteHttpUrl(returnPath)) {
            java.net.URI uri = java.net.URI.create(returnPath.trim());
            base = originOf(returnPath);
            path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        } else {
            base = originOf(appProperties.app().publicBaseUrl());
            if (base == null || base.isBlank()) {
                base = requestOrigin();
            }
            path = returnPath == null || returnPath.isBlank() ? "/sumicare/app/cashier" : returnPath;
        }
        StringBuilder url = new StringBuilder(base).append(path)
                .append("?paymongoReturn=1")
                .append("&").append(ctx.idParam()).append("=").append(enc(ctx.idValue()));
        if (intentId != null && !intentId.isBlank()) {
            url.append("&intent=").append(enc(intentId));
        }
        url.append("&paymentMethod=").append(enc(paymentMethod))
                .append("&amount=").append(amount.toPlainString());
        return url.toString();
    }

    private String requestOrigin() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return "";
            HttpServletRequest request = attributes.getRequest();
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank()) return originOf(origin);
            String host = request.getHeader("X-Forwarded-Host");
            if (host != null && !host.isBlank()) {
                String proto = request.getHeader("X-Forwarded-Proto");
                return (proto != null && !proto.isBlank() ? proto : "https") + "://" + host;
            }
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) return originOf(referer);
        } catch (Exception ignored) {
        }
        return "";
    }

    private boolean isAbsoluteHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String originOf(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                String origin = uri.getScheme() + "://" + uri.getHost();
                if (uri.getPort() != -1) origin += ":" + uri.getPort();
                return origin;
            }
        } catch (Exception ignored) {
        }
        return url.replaceAll("/+$", "");
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private PaymentGateway.Billing toBilling(boolean card, PaymentDetailsRequest details) {
        if (details == null) return null;
        if (card) {
            if (details.cardEmail() == null || details.cardEmail().isBlank()) {
                return null;
            }
            return new PaymentGateway.Billing(details.cardHolder(), details.cardEmail(), null);
        }
        return new PaymentGateway.Billing(details.gcashName(), details.gcashEmail(), details.gcashPhone());
    }

    private PaymentGateway.CardDetails toCardDetails(PaymentDetailsRequest details) {
        if (details == null || details.cardNumber() == null || details.cardNumber().isBlank()) {
            throw new IllegalArgumentException("Card details are required for card payments");
        }
        return new PaymentGateway.CardDetails(
                details.cardNumber().replaceAll("\\s+", ""),
                details.expMonth(), details.expYear(), details.cvc());
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(250L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public record ChargeResult(String intentId, String status, String nextActionUrl) {}

    public record RefundResult(String refundId, String status, BigDecimal amount) {}
}
