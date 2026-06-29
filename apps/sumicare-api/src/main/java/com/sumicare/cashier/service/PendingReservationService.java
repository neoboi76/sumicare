/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class PendingReservationService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(15);
    private static final Duration RESULT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public PendingReservationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String hold(String payloadJson) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(holdKey(token), payloadJson, HOLD_TTL);
        return token;
    }

    public Optional<String> peek(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().get(holdKey(token)));
    }

    public Optional<String> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().getAndDelete(holdKey(token)));
    }

    public void release(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(holdKey(token));
        }
    }

    public void storeResult(String token, String resultJson) {
        redis.opsForValue().set(resultKey(token), resultJson, RESULT_TTL);
    }

    public Optional<String> result(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().get(resultKey(token)));
    }

    private String holdKey(String token) {
        return "pending:res:" + token;
    }

    private String resultKey(String token) {
        return "pending:res:result:" + token;
    }
}
