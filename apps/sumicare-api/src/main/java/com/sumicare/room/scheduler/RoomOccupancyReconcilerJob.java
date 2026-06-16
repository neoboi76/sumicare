/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.room.scheduler;

import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.room.service.RoomOccupancyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RoomOccupancyReconcilerJob {

    private static final Logger log = LoggerFactory.getLogger(RoomOccupancyReconcilerJob.class);

    private final OrganizationRepository organizationRepository;
    private final SessionRepository sessionRepository;
    private final RoomRepository roomRepository;
    private final RoomOccupancyService occupancyService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redis;

    public RoomOccupancyReconcilerJob(OrganizationRepository organizationRepository,
                                      SessionRepository sessionRepository,
                                      RoomRepository roomRepository,
                                      RoomOccupancyService occupancyService,
                                      NotificationService notificationService,
                                      StringRedisTemplate redis) {
        this.organizationRepository = organizationRepository;
        this.sessionRepository = sessionRepository;
        this.roomRepository = roomRepository;
        this.occupancyService = occupancyService;
        this.notificationService = notificationService;
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void reconcile() {
        try {
            organizationRepository.findAll().forEach(org -> reconcileForOrg(org.getId()));
        } catch (Exception e) {
            log.error("RoomOccupancyReconcilerJob failed", e);
        }
    }

    private void reconcileForOrg(UUID orgId) {
        List<Session> activeSessions = sessionRepository.findAllByOrganizationIdAndStatus(orgId, "ACTIVE");

        Set<String> legitimatelyOccupied = new HashSet<>();
        for (Session s : activeSessions) {
            if (s.getEndedAt() != null) {
                log.warn("Reconciler: session {} has ACTIVE status but endedAt is set — marking COMPLETED", s.getId());
                s.setStatus("COMPLETED");
                sessionRepository.save(s);
            } else if (s.getRoomId() != null && s.getBedId() != null) {
                legitimatelyOccupied.add(s.getRoomId() + ":" + s.getBedId());
            }
        }

        // Redis holds the live bed state but a missed session-end (crash, dropped event) can
        // leave a bed marked OCCUPIED forever. SCAN walks every bed key so we can cross-check
        // it against the DB's active sessions and release any orphan, using the non-blocking
        // SCAN cursor rather than KEYS to avoid stalling Redis.
        List<String> redisKeys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match("room:*:bed:*").count(200).build())) {
            while (cursor.hasNext()) redisKeys.add(cursor.next());
        } catch (Exception e) {
            log.warn("Redis SCAN failed in reconciler: {}", e.getMessage());
            return;
        }

        for (String key : redisKeys) {
            try {
                Map<Object, Object> hash = redis.opsForHash().entries(key);
                if (hash.isEmpty() || !"OCCUPIED".equals(hash.get("status"))) continue;

                String[] parts = key.split(":");
                if (parts.length < 4) continue;
                UUID roomId;
                UUID bedId;
                try {
                    roomId = UUID.fromString(parts[1]);
                    bedId = UUID.fromString(parts[3]);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                boolean roomBelongsToOrg = roomRepository.findById(roomId)
                        .map(r -> orgId.equals(r.getOrganizationId())).orElse(false);
                if (!roomBelongsToOrg) continue;

                // An OCCUPIED bed with no backing active session is orphaned, so free it.
                String bedKey = roomId + ":" + bedId;
                if (!legitimatelyOccupied.contains(bedKey)) {
                    log.info("Reconciler: releasing orphaned Redis bed {}/{} with no matching active session", roomId, bedId);
                    occupancyService.release(orgId, roomId, bedId);
                    notificationService.broadcastRoomUpdate(orgId, roomId, bedId,
                            Map.of("event", "BED_RELEASED_BY_RECONCILER"));
                }
            } catch (Exception e) {
                log.warn("Reconciler error processing key {}: {}", key, e.getMessage());
            }
        }
    }
}
