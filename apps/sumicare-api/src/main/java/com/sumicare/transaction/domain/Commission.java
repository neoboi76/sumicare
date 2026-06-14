package com.sumicare.transaction.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commissions")
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "therapist_id", nullable = false, columnDefinition = "uuid")
    private UUID therapistId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "is_extension")
    private boolean extension = false;

    @Column(name = "is_backup")
    private boolean backup = false;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_type", length = 50)
    private String serviceType;

    @Column(name = "specifically_requested")
    private boolean specificallyRequested = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getTherapistId() { return therapistId; }
    public void setTherapistId(UUID therapistId) { this.therapistId = therapistId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isExtension() { return extension; }
    public void setExtension(boolean extension) { this.extension = extension; }
    public boolean isBackup() { return backup; }
    public void setBackup(boolean backup) { this.backup = backup; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public boolean isSpecificallyRequested() { return specificallyRequested; }
    public void setSpecificallyRequested(boolean specificallyRequested) { this.specificallyRequested = specificallyRequested; }
}
