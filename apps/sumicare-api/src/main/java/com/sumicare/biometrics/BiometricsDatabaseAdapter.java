package com.sumicare.biometrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(name = "sumicare.biometrics.mode", havingValue = "database")
public class BiometricsDatabaseAdapter implements BiometricsAdapter {

    private static final String LAST_POLL_KEY = "bio:last_poll";

    private final BiometricsClockInProcessor processor;
    private final StringRedisTemplate redis;

    @Value("${sumicare.biometrics.databaseUrl:}")
    private String databaseUrl;

    @Value("${sumicare.biometrics.databaseUsername:}")
    private String databaseUsername;

    @Value("${sumicare.biometrics.databasePassword:}")
    private String databasePassword;

    @Value("${sumicare.biometrics.databaseQuery:SELECT staff_number, event_time, device_id FROM clock_in_events WHERE event_time > ?}")
    private String query;

    public BiometricsDatabaseAdapter(BiometricsClockInProcessor processor, StringRedisTemplate redis) {
        this.processor = processor;
        this.redis = redis;
    }

    @Override
    public void onClockIn(String staffNumber, OffsetDateTime timestamp, String deviceId) {
        processor.process(staffNumber, timestamp, deviceId);
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void pollDatabase() {
        if (databaseUrl == null || databaseUrl.isBlank()) return;
        String sinceRaw = redis.opsForValue().get(LAST_POLL_KEY);
        OffsetDateTime since = sinceRaw == null
                ? OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                : OffsetDateTime.parse(sinceRaw);
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setObject(1, since);
            try (ResultSet rs = statement.executeQuery()) {
                OffsetDateTime latest = since;
                while (rs.next()) {
                    String staff = rs.getString("staff_number");
                    OffsetDateTime ts = rs.getObject("event_time", OffsetDateTime.class);
                    String device = rs.getString("device_id");
                    if (staff == null || ts == null) continue;
                    processor.process(staff, ts, device);
                    if (ts.isAfter(latest)) latest = ts;
                }
                redis.opsForValue().set(LAST_POLL_KEY,
                        latest.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), Duration.ofDays(7));
            }
        } catch (Exception ignored) {
        }
    }
}
