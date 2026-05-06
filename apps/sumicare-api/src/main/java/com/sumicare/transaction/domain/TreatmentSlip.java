package com.sumicare.transaction.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "treatment_slips")
public class TreatmentSlip {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "tsn", nullable = false)
    private String tsn;

    @Column(name = "booking_id", nullable = false, columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "client_nickname", nullable = false)
    private String clientNickname;

    @Column(name = "locker_number")
    private String lockerNumber;

    @Column(name = "requested_therapist_nickname")
    private String requestedTherapistNickname;

    @Column(name = "primary_therapist_nickname")
    private String primaryTherapistNickname;

    @Column(name = "secondary_therapist_nickname")
    private String secondaryTherapistNickname;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "is_vip")
    private boolean vip = false;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getTsn() { return tsn; }
    public void setTsn(String tsn) { this.tsn = tsn; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getClientNickname() { return clientNickname; }
    public void setClientNickname(String clientNickname) { this.clientNickname = clientNickname; }
    public String getLockerNumber() { return lockerNumber; }
    public void setLockerNumber(String lockerNumber) { this.lockerNumber = lockerNumber; }
    public String getRequestedTherapistNickname() { return requestedTherapistNickname; }
    public void setRequestedTherapistNickname(String requestedTherapistNickname) { this.requestedTherapistNickname = requestedTherapistNickname; }
    public String getPrimaryTherapistNickname() { return primaryTherapistNickname; }
    public void setPrimaryTherapistNickname(String primaryTherapistNickname) { this.primaryTherapistNickname = primaryTherapistNickname; }
    public String getSecondaryTherapistNickname() { return secondaryTherapistNickname; }
    public void setSecondaryTherapistNickname(String secondaryTherapistNickname) { this.secondaryTherapistNickname = secondaryTherapistNickname; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
