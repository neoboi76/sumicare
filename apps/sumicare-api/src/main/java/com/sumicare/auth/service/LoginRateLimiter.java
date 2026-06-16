/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
        // The TTL is set only on the first increment so the one-minute window starts at the
        // first attempt and the whole counter expires together, giving a fresh budget each minute.
        // Callers pass distinct keys (per-ip, per-username, per-ip+username) for independent windows.
        if (count != null && count == 1L) {
            redis.expire(redisKey, Duration.ofMinutes(1));
        }
        return count != null && count <= properties.rateLimit().loginPerMinute();
    }
}
