package com.sumicare.biometrics;

import com.sumicare.common.config.AppProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
                        @RequestBody ClockInRequest request) {
        if (!appProperties.biometrics().sharedKey().equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        OffsetDateTime ts = request.timestamp() == null ? OffsetDateTime.now() : request.timestamp();
        biometricsAdapter.onClockIn(request.staffNumber(), ts, request.deviceId());
    }

    public record ClockInRequest(@NotBlank String staffNumber, OffsetDateTime timestamp, String deviceId) {}
}
