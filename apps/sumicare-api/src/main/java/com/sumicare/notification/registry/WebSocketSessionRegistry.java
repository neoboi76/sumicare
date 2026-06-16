/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.notification.registry;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionRegistry {

    private static final String DECKING_KEY = "ws:sessions:decking";
    private static final String ROOMMAP_KEY = "ws:sessions:roommap";

    private final StringRedisTemplate redis;

    public WebSocketSessionRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        if (sessionId == null) return;
        redis.opsForSet().add(DECKING_KEY, sessionId);
        redis.opsForSet().add(ROOMMAP_KEY, sessionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        redis.opsForSet().remove(DECKING_KEY, sessionId);
        redis.opsForSet().remove(ROOMMAP_KEY, sessionId);
    }
}
