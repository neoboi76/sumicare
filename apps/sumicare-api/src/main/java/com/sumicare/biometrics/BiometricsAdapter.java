package com.sumicare.biometrics;

import java.time.OffsetDateTime;

public interface BiometricsAdapter {
    void onClockIn(String staffNumber, OffsetDateTime timestamp, String deviceId);
}
