package com.sumicare.auth.service;

import com.sumicare.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final AppProperties appProperties;
    private final StringRedisTemplate redis;

    public JwtService(AppProperties appProperties, StringRedisTemplate redis) {
        this.appProperties = appProperties;
        this.redis = redis;
        this.signingKey = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId, UUID organizationId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(appProperties.jwt().accessExpiryMs())))
                .signWith(signingKey)
                .compact();
    }

    public String issueRefreshToken(UUID userId, UUID organizationId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(appProperties.jwt().refreshExpiryMs())))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(revokedKey(jti)));
    }

    public void revoke(String jti, Duration ttl) {
        redis.opsForValue().set(revokedKey(jti), "1", ttl);
    }

    public void revokeAllForUser(UUID userId) {
        Duration ttl = Duration.ofMillis(appProperties.jwt().refreshExpiryMs());
        redis.opsForValue().set("user:" + userId + ":tokens-since",
                String.valueOf(Instant.now().toEpochMilli()), ttl);
    }

    public boolean isTokenIssuedBeforeRevocation(String userId, long issuedAtEpochSecond) {
        String value = redis.opsForValue().get("user:" + userId + ":tokens-since");
        if (value == null) return false;
        try {
            return issuedAtEpochSecond * 1000L < Long.parseLong(value);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Map<String, String> issuePair(UUID userId, UUID organizationId, String role) {
        return Map.of(
                "accessToken", issueAccessToken(userId, organizationId, role),
                "refreshToken", issueRefreshToken(userId, organizationId)
        );
    }

    private String revokedKey(String jti) {
        return "revoked:jti:" + jti;
    }
}
