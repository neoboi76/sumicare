package com.sumicare.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
public class MfaService {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESENDS = 3;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public MfaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Challenge create(UUID userId) {
        String challengeId = UUID.randomUUID().toString();
        String code = generateCode();
        redis.opsForValue().set(userKey(challengeId), userId.toString(), TTL);
        redis.opsForValue().set(codeKey(challengeId), code, TTL);
        redis.opsForValue().set(attemptsKey(challengeId), "0", TTL);
        redis.opsForValue().set(resendsKey(challengeId), "0", TTL);
        return new Challenge(challengeId, userId, code);
    }

    public Challenge resend(String challengeId) {
        String userId = redis.opsForValue().get(userKey(challengeId));
        if (userId == null) {
            throw new BadCredentialsException("Your verification session expired. Please sign in again.");
        }
        Long resends = redis.opsForValue().increment(resendsKey(challengeId));
        if (resends == null || resends > MAX_RESENDS) {
            clear(challengeId);
            throw new BadCredentialsException("Too many code requests. Please sign in again.");
        }
        String code = generateCode();
        redis.opsForValue().set(userKey(challengeId), userId, TTL);
        redis.opsForValue().set(codeKey(challengeId), code, TTL);
        return new Challenge(challengeId, UUID.fromString(userId), code);
    }

    public UUID verify(String challengeId, String code) {
        String userId = redis.opsForValue().get(userKey(challengeId));
        if (userId == null) {
            throw new BadCredentialsException("Your verification code expired. Please sign in again.");
        }
        Long attempts = redis.opsForValue().increment(attemptsKey(challengeId));
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            clear(challengeId);
            throw new BadCredentialsException("Too many incorrect codes. Please sign in again.");
        }
        String expected = redis.opsForValue().get(codeKey(challengeId));
        if (expected == null || code == null || !expected.equals(code.trim())) {
            throw new BadCredentialsException("The verification code is incorrect.");
        }
        clear(challengeId);
        return UUID.fromString(userId);
    }

    private void clear(String challengeId) {
        redis.delete(userKey(challengeId));
        redis.delete(codeKey(challengeId));
        redis.delete(attemptsKey(challengeId));
        redis.delete(resendsKey(challengeId));
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String userKey(String challengeId) {
        return "mfa:challenge:" + challengeId + ":user";
    }

    private String codeKey(String challengeId) {
        return "mfa:challenge:" + challengeId + ":code";
    }

    private String attemptsKey(String challengeId) {
        return "mfa:challenge:" + challengeId + ":attempts";
    }

    private String resendsKey(String challengeId) {
        return "mfa:challenge:" + challengeId + ":resends";
    }

    public record Challenge(String challengeId, UUID userId, String code) {}
}
