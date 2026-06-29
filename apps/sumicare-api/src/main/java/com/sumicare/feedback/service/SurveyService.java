/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.OrderItemAttendee;
import com.sumicare.cashier.repository.OrderItemAttendeeRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.common.util.BaseUrlResolver;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.domain.SurveyInvitation;
import com.sumicare.feedback.dto.SubmitSurveyRequest;
import com.sumicare.feedback.dto.SurveyDetailResponse;
import com.sumicare.feedback.dto.SurveyDetailResponse.TherapistSection;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.feedback.repository.SurveyInvitationRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.service.TipService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SurveyService {

    private final SurveyInvitationRepository invitationRepository;
    private final FeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final OrderItemAttendeeRepository attendeeRepository;
    private final SessionRepository sessionRepository;
    private final TherapistRepository therapistRepository;
    private final TipService tipService;
    private final BaseUrlResolver baseUrlResolver;
    private final ObjectMapper objectMapper;

    private static final int TOKEN_VALID_DAYS = 7;

    public SurveyService(SurveyInvitationRepository invitationRepository,
                         FeedbackRepository feedbackRepository,
                         OrderRepository orderRepository,
                         BookingRepository bookingRepository,
                         OrderItemAttendeeRepository attendeeRepository,
                         SessionRepository sessionRepository,
                         TherapistRepository therapistRepository,
                         TipService tipService,
                         BaseUrlResolver baseUrlResolver,
                         ObjectMapper objectMapper) {
        this.invitationRepository = invitationRepository;
        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.attendeeRepository = attendeeRepository;
        this.sessionRepository = sessionRepository;
        this.therapistRepository = therapistRepository;
        this.tipService = tipService;
        this.baseUrlResolver = baseUrlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String createInvitationLink(UUID organizationId, UUID orderId) {
        SurveyInvitation invitation = invitationRepository.findByOrderId(orderId).orElseGet(() -> {
            SurveyInvitation created = new SurveyInvitation();
            created.setOrganizationId(organizationId);
            created.setOrderId(orderId);
            created.setToken(UUID.randomUUID().toString().replace("-", ""));
            created.setSentAt(OffsetDateTime.now());
            created.setExpiresAt(OffsetDateTime.now().plusDays(TOKEN_VALID_DAYS));
            return invitationRepository.save(created);
        });
        return baseUrlResolver.resolve() + "/survey?token=" + invitation.getToken();
    }

    public SurveyDetailResponse getSurvey(String token) {
        SurveyInvitation invitation = requireActiveInvitation(token);
        Order order = orderRepository.findById(invitation.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String nickname = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).map(Booking::getClientNickname).orElse(null);
        String reference = order.getOrNumber() != null && !order.getOrNumber().isBlank()
                ? order.getOrNumber() : order.getReferenceNumber();

        List<TherapistSection> therapists = resolveTherapists(order.getId());
        return new SurveyDetailResponse(reference, nickname, false, therapists);
    }

    @Transactional
    public void submitSurvey(String token, SubmitSurveyRequest request) {
        SurveyInvitation invitation = requireActiveInvitation(token);
        Order order = orderRepository.findById(invitation.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String nickname = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).map(Booking::getClientNickname).orElse(null);
        Map<UUID, UUID> servedTherapists = servedTherapistSessions(order.getId());

        String orNumber = order.getOrNumber() != null && !order.getOrNumber().isBlank()
                ? order.getOrNumber() : order.getReferenceNumber();

        Feedback lasemaFeedback = buildFeedback(invitation.getOrganizationId(), order.getId(), nickname,
                "LASEMA", null, request.lasemaRating(), request.lasemaComment(),
                toCriteriaJson(request.lasemaCriteria()), orNumber);
        lasemaFeedback.setNpsScore(request.npsScore());
        feedbackRepository.save(lasemaFeedback);

        if (request.therapists() != null) {
            for (SubmitSurveyRequest.TherapistRating tr : request.therapists()) {
                if (tr == null || tr.therapistId() == null) continue;
                feedbackRepository.save(buildFeedback(invitation.getOrganizationId(), order.getId(), nickname,
                        "THERAPIST", tr.therapistId(), tr.rating(), tr.comment(),
                        toCriteriaJson(tr.criteria()), orNumber));
            }
        }

        if (request.tips() != null) {
            for (SubmitSurveyRequest.TherapistTipEntry tip : request.tips()) {
                if (tip == null || tip.therapistId() == null
                        || tip.amount() == null || tip.amount().signum() <= 0) {
                    continue;
                }
                if (!servedTherapists.containsKey(tip.therapistId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A tipped therapist did not serve this visit");
                }
                tipService.recordTip(invitation.getOrganizationId(), tip.therapistId(), order.getId(),
                        servedTherapists.get(tip.therapistId()), tip.amount(),
                        TipService.SOURCE_SURVEY, null);
            }
        }

        invitation.setCompletedAt(OffsetDateTime.now());
        invitationRepository.save(invitation);
    }

    private String toCriteriaJson(Map<String, Integer> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(criteria);
        } catch (Exception e) {
            return null;
        }
    }

    private SurveyInvitation requireActiveInvitation(String token) {
        SurveyInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found"));
        if (invitation.getCompletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found");
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Survey not found");
        }
        return invitation;
    }

    private Feedback buildFeedback(UUID organizationId, UUID orderId, String nickname,
                                   String type, UUID therapistId, int rating, String comment,
                                   String criteriaJson, String orNumber) {
        Feedback feedback = new Feedback();
        feedback.setOrganizationId(organizationId);
        feedback.setOrderId(orderId);
        feedback.setNickname(nickname);
        feedback.setFeedbackType(type);
        feedback.setTherapistId(therapistId);
        feedback.setRatingStars(rating);
        feedback.setComment(comment);
        feedback.setCriteria(criteriaJson);
        feedback.setOrNumber(orNumber);
        feedback.setSubmittedAt(OffsetDateTime.now());
        return feedback;
    }

    private List<TherapistSection> resolveTherapists(UUID orderId) {
        List<TherapistSection> sections = new ArrayList<>();
        for (UUID id : servedTherapistSessions(orderId).keySet()) {
            String nickname = therapistRepository.findById(id).map(t -> t.getNickname()).orElse("Therapist");
            sections.add(new TherapistSection(id, nickname));
        }
        return sections;
    }

    private Map<UUID, UUID> servedTherapistSessions(UUID orderId) {
        Map<UUID, UUID> therapistToSession = new LinkedHashMap<>();
        for (OrderItemAttendee attendee : attendeeRepository.findAllByOrderIdOrderByPosition(orderId)) {
            if (attendee.getSessionId() == null) continue;
            Session session = sessionRepository.findById(attendee.getSessionId()).orElse(null);
            if (session == null) continue;
            if (session.getPrimaryTherapistId() != null) {
                therapistToSession.putIfAbsent(session.getPrimaryTherapistId(), session.getId());
            }
            if (session.getSecondaryTherapistId() != null) {
                therapistToSession.putIfAbsent(session.getSecondaryTherapistId(), session.getId());
            }
        }
        return therapistToSession;
    }
}
