package com.sumicare.auth.service;

import com.sumicare.common.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginRateLimiter {

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean tryConsume(String key) {
        String redisKey = "ratelimit:login:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redis.expire(redisKey, Duration.ofMinutes(1));
        }
        return count != null && count <= properties.rateLimit().loginPerMinute();
    }
}
