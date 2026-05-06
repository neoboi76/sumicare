package com.sumicare.booking.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", nullable = false, columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "primary_therapist_id", columnDefinition = "uuid")
    private UUID primaryTherapistId;

    @Column(name = "secondary_therapist_id", columnDefinition = "uuid")
    private UUID secondaryTherapistId;

    @Column(name = "room_id", columnDefinition = "uuid")
    private UUID roomId;

    @Column(name = "bed_id", columnDefinition = "uuid")
    private UUID bedId;

    @Column(name = "is_specifically_requested", nullable = false)
    private boolean specificallyRequested = false;

    @Column(name = "is_extension", nullable = false)
    private boolean extension = false;

    @Column(name = "extension_minutes")
    private int extensionMinutes = 0;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "expected_end_at")
    private OffsetDateTime expectedEndAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getPrimaryTherapistId() { return primaryTherapistId; }
    public void setPrimaryTherapistId(UUID primaryTherapistId) { this.primaryTherapistId = primaryTherapistId; }
    public UUID getSecondaryTherapistId() { return secondaryTherapistId; }
    public void setSecondaryTherapistId(UUID secondaryTherapistId) { this.secondaryTherapistId = secondaryTherapistId; }
    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }
    public UUID getBedId() { return bedId; }
    public void setBedId(UUID bedId) { this.bedId = bedId; }
    public boolean isSpecificallyRequested() { return specificallyRequested; }
    public void setSpecificallyRequested(boolean specificallyRequested) { this.specificallyRequested = specificallyRequested; }
    public boolean isExtension() { return extension; }
    public void setExtension(boolean extension) { this.extension = extension; }
    public int getExtensionMinutes() { return extensionMinutes; }
    public void setExtensionMinutes(int extensionMinutes) { this.extensionMinutes = extensionMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getExpectedEndAt() { return expectedEndAt; }
    public void setExpectedEndAt(OffsetDateTime expectedEndAt) { this.expectedEndAt = expectedEndAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
