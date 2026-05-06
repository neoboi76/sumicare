package com.sumicare.biometrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Iterator;

@Component
@ConditionalOnProperty(name = "sumicare.biometrics.mode", havingValue = "polling")
public class BiometricsPollingAdapter implements BiometricsAdapter {

    private static final String LAST_POLL_KEY = "bio:last_poll";

    private final BiometricsClockInProcessor processor;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${sumicare.biometrics.pollingUrl:}")
    private String pollingUrl;

    @Value("${sumicare.biometrics.pollingApiKey:}")
    private String pollingApiKey;

    public BiometricsPollingAdapter(BiometricsClockInProcessor processor,
                                    StringRedisTemplate redis,
                                    ObjectMapper objectMapper) {
        this.processor = processor;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onClockIn(String staffNumber, OffsetDateTime timestamp, String deviceId) {
        processor.process(staffNumber, timestamp, deviceId);
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void poll() {
        if (pollingUrl == null || pollingUrl.isBlank()) return;
        String since = redis.opsForValue().get(LAST_POLL_KEY);
        String url = pollingUrl + (pollingUrl.contains("?") ? "&" : "?") + "since=" + (since == null ? "0" : since);
        try {
            String body = restClient.get()
                    .uri(url)
                    .headers(h -> {
                        if (pollingApiKey != null && !pollingApiKey.isBlank()) {
                            h.set("Authorization", "Bearer " + pollingApiKey);
                        }
                    })
                    .retrieve()
                    .body(String.class);
            if (body == null) return;
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.has("events") ? root.get("events") : root;
            if (!events.isArray()) return;
            Iterator<JsonNode> iter = events.elements();
            String latestSeen = since == null ? "0" : since;
            while (iter.hasNext()) {
                JsonNode e = iter.next();
                String staff = e.path("staffNumber").asText(null);
                String ts = e.path("timestamp").asText(null);
                String device = e.path("deviceId").asText(null);
                if (staff == null || ts == null) continue;
                processor.process(staff, OffsetDateTime.parse(ts), device);
                if (ts.compareTo(latestSeen) > 0) latestSeen = ts;
            }
            redis.opsForValue().set(LAST_POLL_KEY, latestSeen, Duration.ofDays(7));
        } catch (Exception ignored) {
        }
    }
}
