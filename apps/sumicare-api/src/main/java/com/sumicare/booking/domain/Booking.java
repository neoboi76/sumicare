/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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

    @Column(name = "reference", length = 8)
    private String reference;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "client_id", columnDefinition = "uuid")
    private UUID clientId;

    @Column(name = "client_nickname", nullable = false)
    private String clientNickname;

    @Column(name = "client_email")
    private String clientEmail;

    @Column(name = "locker_number")
    private String lockerNumber;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "reservation_type", nullable = false)
    private String reservationType;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "preferred_room_id", columnDefinition = "uuid")
    private UUID preferredRoomId;

    @Column(name = "actual_start_at")
    private OffsetDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private OffsetDateTime actualEndAt;

    @Column(name = "pax")
    private Integer pax;

    @Column(name = "client_gender", length = 1)
    private String clientGender;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    @Column(name = "preferred_therapist", length = 120)
    private String preferredTherapist;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "UNPAID";

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getClientNickname() { return clientNickname; }
    public void setClientNickname(String clientNickname) { this.clientNickname = clientNickname; }
    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }
    public String getLockerNumber() { return lockerNumber; }
    public void setLockerNumber(String lockerNumber) { this.lockerNumber = lockerNumber; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getReservationType() { return reservationType; }
    public void setReservationType(String reservationType) { this.reservationType = reservationType; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public UUID getPreferredRoomId() { return preferredRoomId; }
    public void setPreferredRoomId(UUID preferredRoomId) { this.preferredRoomId = preferredRoomId; }
    public OffsetDateTime getActualStartAt() { return actualStartAt; }
    public void setActualStartAt(OffsetDateTime actualStartAt) { this.actualStartAt = actualStartAt; }
    public OffsetDateTime getActualEndAt() { return actualEndAt; }
    public void setActualEndAt(OffsetDateTime actualEndAt) { this.actualEndAt = actualEndAt; }
    public Integer getPax() { return pax; }
    public void setPax(Integer pax) { this.pax = pax; }
    public String getClientGender() { return clientGender; }
    public void setClientGender(String clientGender) { this.clientGender = clientGender; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public OffsetDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(OffsetDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }
    public String getPreferredTherapist() { return preferredTherapist; }
    public void setPreferredTherapist(String preferredTherapist) { this.preferredTherapist = preferredTherapist; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
