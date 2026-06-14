package com.sumicare.attendance.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "therapist_attendance")
public class TherapistAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "therapist_id", nullable = false, columnDefinition = "uuid")
    private UUID therapistId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_at", nullable = false)
    private OffsetDateTime eventAt;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "remarks")
    private String remarks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTherapistId() { return therapistId; }
    public void setTherapistId(UUID therapistId) { this.therapistId = therapistId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public OffsetDateTime getEventAt() { return eventAt; }
    public void setEventAt(OffsetDateTime eventAt) { this.eventAt = eventAt; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
