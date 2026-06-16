/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sumicare")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Anthropic anthropic,
        Bcrypt bcrypt,
        RateLimit rateLimit,
        App app,
        Payment payment,
        Email email
) {
    public record Jwt(String secret, long accessExpiryMs, long refreshExpiryMs) {}
    public record Cors(String allowedOrigins) {}
    public record Anthropic(String apiKey) {}
    public record Bcrypt(int cost) {}
    public record RateLimit(int loginPerMinute) {}
    public record App(String publicBaseUrl, String emailFrom) {}
    public record Payment(Paymongo paymongo) {
        public record Paymongo(String secretKey, String publicKey, String webhookSecret, boolean mockMode) {}
    }
    public record Email(String provider, String fromName, String brevoApiKey) {}
}
