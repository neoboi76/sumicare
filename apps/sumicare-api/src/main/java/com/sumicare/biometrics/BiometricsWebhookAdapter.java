package com.sumicare.biometrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Primary
@Component
@ConditionalOnProperty(name = "sumicare.biometrics.mode", havingValue = "webhook", matchIfMissing = true)
public class BiometricsWebhookAdapter implements BiometricsAdapter {

    private final BiometricsClockInProcessor processor;

    public BiometricsWebhookAdapter(BiometricsClockInProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void onClockIn(String staffNumber, OffsetDateTime timestamp, String deviceId) {
        processor.process(staffNumber, timestamp, deviceId);
    }
}
