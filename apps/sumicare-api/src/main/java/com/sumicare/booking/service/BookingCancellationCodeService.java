package com.sumicare.booking.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
public class BookingCancellationCodeService {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public BookingCancellationCodeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean onCooldown(UUID bookingId) {
        return Boolean.TRUE.equals(redis.hasKey(cooldownKey(bookingId)));
    }

    public String issue(UUID bookingId, String email) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(bookingId), code + ":" + email.trim().toLowerCase(), TTL);
        redis.delete(attemptsKey(bookingId));
        redis.opsForValue().set(cooldownKey(bookingId), "1", RESEND_COOLDOWN);
        return code;
    }

    public boolean verify(UUID bookingId, String email, String code) {
        String stored = redis.opsForValue().get(codeKey(bookingId));
        if (stored == null) {
            return false;
        }
        Long attempts = redis.opsForValue().increment(attemptsKey(bookingId));
        if (attempts != null && attempts == 1L) {
            redis.expire(attemptsKey(bookingId), TTL);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            clear(bookingId);
            return false;
        }
        String expected = code.trim() + ":" + email.trim().toLowerCase();
        return stored.equals(expected);
    }

    public void clear(UUID bookingId) {
        redis.delete(codeKey(bookingId));
        redis.delete(attemptsKey(bookingId));
    }

    private String codeKey(UUID bookingId) {
        return "cancel:code:" + bookingId;
    }

    private String attemptsKey(UUID bookingId) {
        return "cancel:attempts:" + bookingId;
    }

    private String cooldownKey(UUID bookingId) {
        return "cancel:cooldown:" + bookingId;
    }
}
