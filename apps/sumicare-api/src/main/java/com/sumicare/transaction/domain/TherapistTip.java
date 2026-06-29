/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "therapist_tips")
public class TherapistTip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "therapist_id", nullable = false, columnDefinition = "uuid")
    private UUID therapistId;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "recorded_by_user_id", columnDefinition = "uuid")
    private UUID recordedByUserId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getTherapistId() { return therapistId; }
    public void setTherapistId(UUID therapistId) { this.therapistId = therapistId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public UUID getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(UUID recordedByUserId) { this.recordedByUserId = recordedByUserId; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
