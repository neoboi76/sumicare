/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.room.service;

import com.sumicare.notification.service.NotificationService;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomOccupancyService {

    private final StringRedisTemplate redis;
    private final NotificationService notificationService;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    public RoomOccupancyService(StringRedisTemplate redis,
                                NotificationService notificationService,
                                RoomRepository roomRepository,
                                BedRepository bedRepository) {
        this.redis = redis;
        this.notificationService = notificationService;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
    }

    public long countOccupiedBeds(UUID organizationId) {
        List<Room> rooms = roomRepository.findAllByOrganizationId(organizationId);
        long count = 0;
        for (Room r : rooms) {
            for (Bed b : bedRepository.findAllByRoomId(r.getId())) {
                Map<Object, Object> entries = read(r.getId(), b.getId());
                Object status = entries == null ? null : entries.get("status");
                if (status != null && "OCCUPIED".equals(status.toString())) {
                    count++;
                }
            }
        }
        return count;
    }

    public String key(UUID roomId, UUID bedId) {
        return "room:" + roomId + ":bed:" + bedId;
    }

    // On session start the live bed state is written as one Redis hash so SSE/room-map reads
    // never touch PostgreSQL. genderLock records the admitted gender, which the room-map layer
    // uses to keep gender-segregated common rooms consistent across remaining beds.
    public void occupy(UUID organizationId, UUID roomId, UUID bedId, String clientNickname,
                       String lockerNumber, String therapistNickname, String genderLock, UUID ownerItemId) {
        Map<String, String> hash = new HashMap<>();
        hash.put("status", "OCCUPIED");
        hash.put("clientNickname", clientNickname == null ? "" : clientNickname);
        hash.put("lockerNumber", lockerNumber == null ? "" : lockerNumber);
        hash.put("therapistNickname", therapistNickname == null ? "" : therapistNickname);
        hash.put("genderLock", genderLock == null ? "" : genderLock);
        hash.put("ownerItemId", ownerItemId == null ? "" : ownerItemId.toString());
        hash.put("startedAt", String.valueOf(Instant.now().toEpochMilli()));
        redis.opsForHash().putAll(key(roomId, bedId), hash);
        notificationService.broadcastRoomUpdate(organizationId, roomId, bedId, hash);
    }

    // Session end invalidates the bed by deleting the whole hash; absence of the key is what
    // marks the bed AVAILABLE again (the broadcast carries that status to live listeners).
    public void release(UUID organizationId, UUID roomId, UUID bedId) {
        redis.delete(key(roomId, bedId));
        notificationService.broadcastRoomUpdate(organizationId, roomId, bedId, Map.of("status", "AVAILABLE"));
    }

    public Map<Object, Object> read(UUID roomId, UUID bedId) {
        HashOperations<String, Object, Object> ops = redis.opsForHash();
        return ops.entries(key(roomId, bedId));
    }
}
