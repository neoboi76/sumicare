package com.sumicare.cashier.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "transactor_name", length = 120)
    private String transactorName;

    @Column(name = "is_group_booking", nullable = false)
    private boolean groupBooking = false;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType = "COMMON";

    @Column(name = "room_type_charge", nullable = false)
    private BigDecimal roomTypeCharge = BigDecimal.ZERO;

    @Column(name = "is_weekend", nullable = false)
    private boolean weekend = false;

    @Column(name = "treatment_slip_id", columnDefinition = "uuid")
    private UUID treatmentSlipId;

    @Column(name = "cashier_user_id", columnDefinition = "uuid")
    private UUID cashierUserId;

    @Column(name = "last_edited_by_user_id", columnDefinition = "uuid")
    private UUID lastEditedByUserId;

    @Column(name = "or_number", length = 50)
    private String orNumber;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "preferred_therapist", length = 120)
    private String preferredTherapist;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "total", nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "extension_amount", nullable = false)
    private BigDecimal extensionAmount = BigDecimal.ZERO;

    @Column(name = "extension_minutes", nullable = false)
    private int extensionMinutes = 0;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @Column(name = "voucher_id", columnDefinition = "uuid")
    private UUID voucherId;

    @Column(name = "completion_email_sent_at")
    private OffsetDateTime completionEmailSentAt;

    @Column(name = "payment_email_sent_at")
    private OffsetDateTime paymentEmailSentAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getTreatmentSlipId() { return treatmentSlipId; }
    public void setTreatmentSlipId(UUID treatmentSlipId) { this.treatmentSlipId = treatmentSlipId; }
    public UUID getCashierUserId() { return cashierUserId; }
    public void setCashierUserId(UUID cashierUserId) { this.cashierUserId = cashierUserId; }
    public UUID getLastEditedByUserId() { return lastEditedByUserId; }
    public void setLastEditedByUserId(UUID lastEditedByUserId) { this.lastEditedByUserId = lastEditedByUserId; }
    public String getOrNumber() { return orNumber; }
    public void setOrNumber(String orNumber) { this.orNumber = orNumber; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPreferredTherapist() { return preferredTherapist; }
    public void setPreferredTherapist(String preferredTherapist) { this.preferredTherapist = preferredTherapist; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getExtensionAmount() { return extensionAmount; }
    public void setExtensionAmount(BigDecimal extensionAmount) { this.extensionAmount = extensionAmount; }
    public int getExtensionMinutes() { return extensionMinutes; }
    public void setExtensionMinutes(int extensionMinutes) { this.extensionMinutes = extensionMinutes; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelledReason() { return cancelledReason; }
    public void setCancelledReason(String cancelledReason) { this.cancelledReason = cancelledReason; }
    public String getTransactorName() { return transactorName; }
    public void setTransactorName(String transactorName) { this.transactorName = transactorName; }
    public boolean isGroupBooking() { return groupBooking; }
    public void setGroupBooking(boolean groupBooking) { this.groupBooking = groupBooking; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public BigDecimal getRoomTypeCharge() { return roomTypeCharge; }
    public void setRoomTypeCharge(BigDecimal roomTypeCharge) { this.roomTypeCharge = roomTypeCharge; }
    public boolean isWeekend() { return weekend; }
    public void setWeekend(boolean weekend) { this.weekend = weekend; }
    public UUID getVoucherId() { return voucherId; }
    public void setVoucherId(UUID voucherId) { this.voucherId = voucherId; }
    public OffsetDateTime getCompletionEmailSentAt() { return completionEmailSentAt; }
    public void setCompletionEmailSentAt(OffsetDateTime completionEmailSentAt) { this.completionEmailSentAt = completionEmailSentAt; }
    public OffsetDateTime getPaymentEmailSentAt() { return paymentEmailSentAt; }
    public void setPaymentEmailSentAt(OffsetDateTime paymentEmailSentAt) { this.paymentEmailSentAt = paymentEmailSentAt; }
}
