package com.sumicare.notification.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final SimpMessagingTemplate template;

    public NotificationService(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void broadcastDecking(UUID organizationId, Object payload) {
        template.convertAndSend("/topic/decking-updates/" + organizationId, payload);
    }

    public void broadcastRoomUpdate(UUID organizationId, UUID roomId, UUID bedId, Map<String, ?> payload) {
        template.convertAndSend("/topic/room-updates/" + organizationId,
                Map.of("roomId", roomId, "bedId", bedId, "state", payload));
    }
}
