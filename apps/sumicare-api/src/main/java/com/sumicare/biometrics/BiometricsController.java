package com.sumicare.biometrics;

import com.sumicare.common.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/biometrics")
public class BiometricsController {

    private final BiometricsAdapter biometricsAdapter;
    private final AppProperties appProperties;

    public BiometricsController(BiometricsAdapter biometricsAdapter, AppProperties appProperties) {
        this.biometricsAdapter = biometricsAdapter;
        this.appProperties = appProperties;
    }

    @PostMapping("/clock-in")
    public void clockIn(@RequestHeader(name = "X-Biometrics-Key", required = false) String key,
                        @Valid @RequestBody ClockInRequest request) {
        if (!isAuthorized(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        OffsetDateTime ts = request.timestamp() == null ? OffsetDateTime.now() : request.timestamp();
        biometricsAdapter.onClockIn(request.staffNumber(), ts, request.deviceId());
    }

    private boolean isAuthorized(String key) {
        String expected = appProperties.biometrics().sharedKey();
        if (expected == null || expected.isBlank() || key == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8));
    }

    public record ClockInRequest(@NotBlank String staffNumber, OffsetDateTime timestamp, String deviceId) {}
}
