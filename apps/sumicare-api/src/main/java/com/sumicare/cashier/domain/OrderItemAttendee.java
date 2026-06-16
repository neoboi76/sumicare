/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_item_attendees")
public class OrderItemAttendee {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_item_id", nullable = false, columnDefinition = "uuid")
    private UUID orderItemId;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "package_tier_id")
    private Long packageTierId;

    @Column(name = "locker_number", length = 16)
    private String lockerNumber;

    @Column(name = "client_gender", length = 1)
    private String clientGender;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "treatment_slip_id", columnDefinition = "uuid")
    private UUID treatmentSlipId;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal discount = java.math.BigDecimal.ZERO;

    @Column(name = "provided_tsn", length = 64)
    private String providedTsn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public Long getPackageTierId() { return packageTierId; }
    public void setPackageTierId(Long packageTierId) { this.packageTierId = packageTierId; }
    public String getLockerNumber() { return lockerNumber; }
    public void setLockerNumber(String lockerNumber) { this.lockerNumber = lockerNumber; }
    public String getClientGender() { return clientGender; }
    public void setClientGender(String clientGender) { this.clientGender = clientGender; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getTreatmentSlipId() { return treatmentSlipId; }
    public void setTreatmentSlipId(UUID treatmentSlipId) { this.treatmentSlipId = treatmentSlipId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public java.math.BigDecimal getDiscount() { return discount; }
    public void setDiscount(java.math.BigDecimal discount) { this.discount = discount == null ? java.math.BigDecimal.ZERO : discount; }
    public String getProvidedTsn() { return providedTsn; }
    public void setProvidedTsn(String providedTsn) { this.providedTsn = providedTsn; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
