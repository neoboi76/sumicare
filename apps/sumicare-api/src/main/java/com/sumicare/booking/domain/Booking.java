package com.sumicare.booking.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "client_id", columnDefinition = "uuid")
    private UUID clientId;

    @Column(name = "client_nickname", nullable = false)
    private String clientNickname;

    @Column(name = "locker_number")
    private String lockerNumber;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "reservation_type", nullable = false)
    private String reservationType;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "actual_start_at")
    private OffsetDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private OffsetDateTime actualEndAt;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getClientNickname() { return clientNickname; }
    public void setClientNickname(String clientNickname) { this.clientNickname = clientNickname; }
    public String getLockerNumber() { return lockerNumber; }
    public void setLockerNumber(String lockerNumber) { this.lockerNumber = lockerNumber; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getReservationType() { return reservationType; }
    public void setReservationType(String reservationType) { this.reservationType = reservationType; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getActualStartAt() { return actualStartAt; }
    public void setActualStartAt(OffsetDateTime actualStartAt) { this.actualStartAt = actualStartAt; }
    public OffsetDateTime getActualEndAt() { return actualEndAt; }
    public void setActualEndAt(OffsetDateTime actualEndAt) { this.actualEndAt = actualEndAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
