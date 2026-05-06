package com.sumicare.room.service;

import com.sumicare.notification.service.NotificationService;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomOccupancyService {

    private final StringRedisTemplate redis;
    private final NotificationService notificationService;

    public RoomOccupancyService(StringRedisTemplate redis, NotificationService notificationService) {
        this.redis = redis;
        this.notificationService = notificationService;
    }

    public String key(UUID roomId, UUID bedId) {
        return "room:" + roomId + ":bed:" + bedId;
    }

    public void occupy(UUID organizationId, UUID roomId, UUID bedId, String clientNickname,
                       String lockerNumber, String therapistNickname, String genderLock) {
        Map<String, String> hash = new HashMap<>();
        hash.put("status", "OCCUPIED");
        hash.put("clientNickname", clientNickname == null ? "" : clientNickname);
        hash.put("lockerNumber", lockerNumber == null ? "" : lockerNumber);
        hash.put("therapistNickname", therapistNickname == null ? "" : therapistNickname);
        hash.put("genderLock", genderLock == null ? "" : genderLock);
        hash.put("startedAt", String.valueOf(Instant.now().toEpochMilli()));
        redis.opsForHash().putAll(key(roomId, bedId), hash);
        notificationService.broadcastRoomUpdate(organizationId, roomId, bedId, hash);
    }

    public void release(UUID organizationId, UUID roomId, UUID bedId) {
        redis.delete(key(roomId, bedId));
        notificationService.broadcastRoomUpdate(organizationId, roomId, bedId, Map.of("status", "AVAILABLE"));
    }

    public Map<Object, Object> read(UUID roomId, UUID bedId) {
        HashOperations<String, Object, Object> ops = redis.opsForHash();
        return ops.entries(key(roomId, bedId));
    }
}
