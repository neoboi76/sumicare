package com.sumicare.transaction.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
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

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "pax")
    private Integer pax;

    @Column(name = "treatment_minutes")
    private Integer treatmentMinutes;

    @Column(name = "jacuzzi_minutes")
    private Integer jacuzziMinutes;

    @Column(name = "massage_minutes")
    private Integer massageMinutes;

    @Column(name = "wine_included")
    private Boolean wineIncluded;

    @Column(name = "or_number", length = 64)
    private String orNumber;

    @Column(name = "add_on_or_number", length = 64)
    private String addOnOrNumber;

    @Column(name = "others_add_on", columnDefinition = "text")
    private String othersAddOn;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "waiver_accepted", nullable = false)
    private boolean waiverAccepted = false;

    @Column(name = "waiver_accepted_at")
    private OffsetDateTime waiverAcceptedAt;

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
    public Integer getPax() { return pax; }
    public void setPax(Integer pax) { this.pax = pax; }
    public Integer getTreatmentMinutes() { return treatmentMinutes; }
    public void setTreatmentMinutes(Integer treatmentMinutes) { this.treatmentMinutes = treatmentMinutes; }
    public Integer getJacuzziMinutes() { return jacuzziMinutes; }
    public void setJacuzziMinutes(Integer jacuzziMinutes) { this.jacuzziMinutes = jacuzziMinutes; }
    public Integer getMassageMinutes() { return massageMinutes; }
    public void setMassageMinutes(Integer massageMinutes) { this.massageMinutes = massageMinutes; }
    public Boolean getWineIncluded() { return wineIncluded; }
    public void setWineIncluded(Boolean wineIncluded) { this.wineIncluded = wineIncluded; }
    public String getOrNumber() { return orNumber; }
    public void setOrNumber(String orNumber) { this.orNumber = orNumber; }
    public String getAddOnOrNumber() { return addOnOrNumber; }
    public void setAddOnOrNumber(String addOnOrNumber) { this.addOnOrNumber = addOnOrNumber; }
    public String getOthersAddOn() { return othersAddOn; }
    public void setOthersAddOn(String othersAddOn) { this.othersAddOn = othersAddOn; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public boolean isWaiverAccepted() { return waiverAccepted; }
    public void setWaiverAccepted(boolean waiverAccepted) { this.waiverAccepted = waiverAccepted; }
    public OffsetDateTime getWaiverAcceptedAt() { return waiverAcceptedAt; }
    public void setWaiverAcceptedAt(OffsetDateTime waiverAcceptedAt) { this.waiverAcceptedAt = waiverAcceptedAt; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
