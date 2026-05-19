package com.sumicare.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sumicare")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Biometrics biometrics,
        Anthropic anthropic,
        Bcrypt bcrypt,
        RateLimit rateLimit,
        App app,
        Payment payment
) {
    public record Jwt(String secret, long accessExpiryMs, long refreshExpiryMs) {}
    public record Cors(String allowedOrigins) {}
    public record Biometrics(String sharedKey) {}
    public record Anthropic(String apiKey) {}
    public record Bcrypt(int cost) {}
    public record RateLimit(int loginPerMinute) {}
    public record App(String publicBaseUrl, String emailFrom) {}
    public record Payment(Paymongo paymongo) {
        public record Paymongo(String secretKey, String webhookSecret, boolean mockMode) {}
    }
}
