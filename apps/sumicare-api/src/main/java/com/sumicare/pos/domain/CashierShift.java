package com.sumicare.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cashier_shifts")
public class CashierShift {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "cashier_user_id", nullable = false, columnDefinition = "uuid")
    private UUID cashierUserId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "opening_float")
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "closing_total")
    private BigDecimal closingTotal;

    @Column(name = "variance")
    private BigDecimal variance;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getCashierUserId() { return cashierUserId; }
    public void setCashierUserId(UUID cashierUserId) { this.cashierUserId = cashierUserId; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }
    public BigDecimal getClosingTotal() { return closingTotal; }
    public void setClosingTotal(BigDecimal closingTotal) { this.closingTotal = closingTotal; }
    public BigDecimal getVariance() { return variance; }
    public void setVariance(BigDecimal variance) { this.variance = variance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
