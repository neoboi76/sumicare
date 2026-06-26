/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "client_id", columnDefinition = "uuid")
    private UUID clientId;

    @Column(name = "rating_stars", nullable = false)
    private int ratingStars;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "nickname", length = 120)
    private String nickname;

    @Column(name = "or_number", length = 50)
    private String orNumber;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "read_by_user_id", columnDefinition = "uuid")
    private UUID readByUserId;

    @Column(name = "feedback_type", nullable = false, length = 16)
    private String feedbackType = "GENERAL";

    @Column(name = "therapist_id", columnDefinition = "uuid")
    private UUID therapistId;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "staff_response", columnDefinition = "text")
    private String staffResponse;

    @Column(name = "responded_by_user_id", columnDefinition = "uuid")
    private UUID respondedByUserId;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public int getRatingStars() { return ratingStars; }
    public void setRatingStars(int ratingStars) { this.ratingStars = ratingStars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getOrNumber() { return orNumber; }
    public void setOrNumber(String orNumber) { this.orNumber = orNumber; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
    public UUID getReadByUserId() { return readByUserId; }
    public void setReadByUserId(UUID readByUserId) { this.readByUserId = readByUserId; }
    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
    public UUID getTherapistId() { return therapistId; }
    public void setTherapistId(UUID therapistId) { this.therapistId = therapistId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getStaffResponse() { return staffResponse; }
    public void setStaffResponse(String staffResponse) { this.staffResponse = staffResponse; }
    public UUID getRespondedByUserId() { return respondedByUserId; }
    public void setRespondedByUserId(UUID respondedByUserId) { this.respondedByUserId = respondedByUserId; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }
}
