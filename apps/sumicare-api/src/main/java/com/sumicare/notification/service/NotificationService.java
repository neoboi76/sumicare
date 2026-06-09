package com.sumicare.notification.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
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

    public void broadcastBookingEvent(UUID organizationId, String event, UUID bookingId, String summary) {
        Map<String, Object> body = payload(event, bookingId, "bookingId", summary);
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        template.convertAndSend("/topic/bookings/" + organizationId, body);
    }

    public void broadcastOrderEvent(UUID organizationId, String event, UUID orderId, String summary) {
        Map<String, Object> body = payload(event, orderId, "orderId", summary);
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        template.convertAndSend("/topic/orders/" + organizationId, body);
    }

    public void broadcastMessageEvent(UUID organizationId, String event, UUID messageId, String summary) {
        Map<String, Object> body = payload(event, messageId, "messageId", summary);
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        template.convertAndSend("/topic/messages/" + organizationId, body);
    }

    public void broadcastFeedbackEvent(UUID organizationId, String event, UUID feedbackId, String summary) {
        Map<String, Object> body = payload(event, feedbackId, "feedbackId", summary);
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        template.convertAndSend("/topic/feedback/" + organizationId, body);
    }

    private Map<String, Object> payload(String event, UUID id, String idKey, String summary) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", event);
        body.put(idKey, id == null ? null : id.toString());
        body.put("summary", summary == null ? "" : summary);
        body.put("at", OffsetDateTime.now().toString());
        return body;
    }
}
