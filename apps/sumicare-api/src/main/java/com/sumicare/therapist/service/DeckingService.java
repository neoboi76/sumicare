package com.sumicare.therapist.service;

import com.sumicare.notification.service.NotificationService;
import com.sumicare.therapist.dto.DeckingEntry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeckingService {

    private static final String QUEUE_KEY_PREFIX = "decking:active:";
    private static final String SHIFT_MEMBERS_PREFIX = "decking:shift:";
    private static final String SKIP_PREFIX = "decking:skip:";
    private static final String FLAG_PREFIX = "decking:flag:";

    private final StringRedisTemplate redis;
    private final NotificationService notificationService;

    public DeckingService(StringRedisTemplate redis, NotificationService notificationService) {
        this.redis = redis;
        this.notificationService = notificationService;
    }

    public String queueKey(UUID organizationId) {
        return QUEUE_KEY_PREFIX + organizationId;
    }

    public String shiftMembersKey(UUID organizationId, Long shiftId) {
        return SHIFT_MEMBERS_PREFIX + organizationId + ":" + shiftId;
    }

    public String skipKey(UUID organizationId, UUID therapistId) {
        return SKIP_PREFIX + organizationId + ":" + therapistId;
    }

    public String flagKey(UUID organizationId, UUID therapistId) {
        return FLAG_PREFIX + organizationId + ":" + therapistId;
    }

    public void appendToBack(UUID organizationId, UUID therapistId, Long shiftId) {
        double score = Instant.now().toEpochMilli();
        redis.opsForZSet().add(queueKey(organizationId), therapistId.toString(), score);
        if (shiftId != null) {
            redis.opsForSet().add(shiftMembersKey(organizationId, shiftId), therapistId.toString());
        }
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void prependToFront(UUID organizationId, UUID therapistId, Long shiftId) {
        double score = lowestScore(organizationId) - 1.0;
        redis.opsForZSet().add(queueKey(organizationId), therapistId.toString(), score);
        if (shiftId != null) {
            redis.opsForSet().add(shiftMembersKey(organizationId, shiftId), therapistId.toString());
        }
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void rotateToBack(UUID organizationId, UUID therapistId) {
        Double current = redis.opsForZSet().score(queueKey(organizationId), therapistId.toString());
        if (current == null) return;
        redis.opsForZSet().add(queueKey(organizationId), therapistId.toString(), Instant.now().toEpochMilli());
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void servedRequested(UUID organizationId, UUID therapistId) {
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void remove(UUID organizationId, UUID therapistId) {
        redis.opsForZSet().remove(queueKey(organizationId), therapistId.toString());
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void skip(UUID organizationId, UUID therapistId, Duration duration) {
        redis.opsForValue().set(skipKey(organizationId, therapistId), "1", duration);
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void cancelSkip(UUID organizationId, UUID therapistId) {
        redis.delete(skipKey(organizationId, therapistId));
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public void setFlag(UUID organizationId, UUID therapistId, DeckingFlag flag) {
        redis.opsForValue().set(flagKey(organizationId, therapistId), flag.name());
    }

    public void clearFlag(UUID organizationId, UUID therapistId) {
        redis.delete(flagKey(organizationId, therapistId));
    }

    public void insertBackup(UUID organizationId, UUID therapistId, int positionFromTop) {
        Set<ZSetOperations.TypedTuple<String>> existing = redis.opsForZSet()
                .rangeWithScores(queueKey(organizationId), 0, -1);
        if (existing == null || existing.isEmpty()) {
            appendToBack(organizationId, therapistId, null);
            return;
        }
        List<ZSetOperations.TypedTuple<String>> ordered = new java.util.ArrayList<>(existing);
        int index = Math.max(0, Math.min(positionFromTop, ordered.size()));
        double previous = index == 0 ? ordered.get(0).getScore() - 1.0 : ordered.get(index - 1).getScore();
        double next = index >= ordered.size() ? previous + 2.0 : ordered.get(index).getScore();
        double score = (previous + next) / 2.0;
        redis.opsForZSet().add(queueKey(organizationId), therapistId.toString(), score);
        setFlag(organizationId, therapistId, DeckingFlag.BACKUP);
        notificationService.broadcastDecking(organizationId, currentLineup(organizationId));
    }

    public List<DeckingEntry> currentLineup(UUID organizationId) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .rangeWithScores(queueKey(organizationId), 0, -1);
        if (tuples == null) return List.of();
        Map<UUID, DeckingFlag> flags = new LinkedHashMap<>();
        return tuples.stream()
                .filter(t -> t.getValue() != null)
                .map(t -> {
                    UUID id = UUID.fromString(t.getValue());
                    String flagRaw = redis.opsForValue().get(flagKey(organizationId, id));
                    DeckingFlag flag = flagRaw == null ? DeckingFlag.NONE : DeckingFlag.valueOf(flagRaw);
                    flags.put(id, flag);
                    boolean skipped = Boolean.TRUE.equals(redis.hasKey(skipKey(organizationId, id)));
                    return new DeckingEntry(id, t.getScore() == null ? 0.0 : t.getScore(), flag.name(), skipped);
                })
                .toList();
    }

    private double lowestScore(UUID organizationId) {
        Set<ZSetOperations.TypedTuple<String>> head = redis.opsForZSet()
                .rangeWithScores(queueKey(organizationId), 0, 0);
        if (head == null || head.isEmpty()) return Instant.now().toEpochMilli();
        ZSetOperations.TypedTuple<String> first = head.iterator().next();
        return first.getScore() == null ? Instant.now().toEpochMilli() : first.getScore();
    }

    public boolean isSkipped(UUID organizationId, UUID therapistId) {
        return Boolean.TRUE.equals(redis.hasKey(skipKey(organizationId, therapistId)));
    }

    public enum DeckingFlag {
        NONE,
        REQUESTED,
        SCRUB,
        ORDINARY,
        BACKUP,
        MANUAL
    }
}
