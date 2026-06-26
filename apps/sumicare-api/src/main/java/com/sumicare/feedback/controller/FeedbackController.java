/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class FeedbackController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final FeedbackRepository repository;
    private final OrganizationRepository organizationRepository;
    private final NotificationService notificationService;
    private final TherapistRepository therapistRepository;

    public FeedbackController(FeedbackRepository repository, OrganizationRepository organizationRepository,
                              NotificationService notificationService, TherapistRepository therapistRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.notificationService = notificationService;
        this.therapistRepository = therapistRepository;
    }

    @PostMapping("/api/public/feedback/{slug}")
    public Feedback submit(@PathVariable String slug, @RequestBody PublicFeedbackRequest request) {
        UUID orgId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        Feedback feedback = new Feedback();
        feedback.setOrganizationId(orgId);
        feedback.setRatingStars(request.ratingStars());
        feedback.setComment(request.comment());
        if (request.nickname() != null && !request.nickname().isBlank()) {
            feedback.setNickname(request.nickname().trim());
        }
        if (request.orNumber() != null && !request.orNumber().isBlank()) {
            feedback.setOrNumber(request.orNumber().trim());
        }
        Feedback saved = repository.save(feedback);
        notificationService.broadcastFeedbackEvent(orgId, "FEEDBACK_RECEIVED", saved.getId(),
                "New feedback (" + saved.getRatingStars() + " stars)");
        return saved;
    }

    @GetMapping("/api/public/feedback/{slug}")
    public List<Feedback> recent(@PathVariable String slug) {
        return organizationRepository.findBySlug(slug)
                .map(o -> repository.findTop20ByOrganizationIdOrderBySubmittedAtDesc(o.getId()))
                .orElseGet(List::of);
    }

    @GetMapping("/api/feedback")
    public Page<Feedback> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return repository.findAllByOrganizationIdOrderBySubmittedAtDesc(
                UUID.fromString(principal.organizationId()),
                PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/api/feedback/unread-count")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return Map.of("count", repository.countByOrganizationIdAndReadAtIsNull(
                UUID.fromString(principal.organizationId())));
    }

    @PostMapping("/api/feedback/mark-all-read")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        int updated = repository.markAllRead(
                UUID.fromString(principal.organizationId()),
                OffsetDateTime.now(),
                UUID.fromString(principal.userId()));
        return Map.of("marked", updated);
    }

    @PostMapping("/api/feedback/{id}/respond")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Feedback respond(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                            @PathVariable UUID id, @RequestBody RespondRequest request) {
        UUID orgId = UUID.fromString(principal.organizationId());
        Feedback feedback = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!feedback.getOrganizationId().equals(orgId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        feedback.setStaffResponse(request.response());
        feedback.setRespondedByUserId(UUID.fromString(principal.userId()));
        feedback.setRespondedAt(OffsetDateTime.now());
        return repository.save(feedback);
    }

    @GetMapping(value = "/api/feedback/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID therapistId) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null
                ? LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime()
                : OffsetDateTime.now().minusYears(10);
        OffsetDateTime end = to != null
                ? LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime()
                : OffsetDateTime.now().plusDays(1);
        String orgName = organizationRepository.findById(orgId).map(o -> o.getDisplayName()).orElse("Organization");
        List<Feedback> rows = repository.findAllByOrganizationIdAndSubmittedAtBetweenOrderBySubmittedAtAsc(orgId, start, end).stream()
                .filter(f -> type == null || type.isBlank() || type.equalsIgnoreCase(f.getFeedbackType()))
                .filter(f -> therapistId == null || therapistId.equals(f.getTherapistId()))
                .toList();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(fmt);
        StringBuilder csv = new StringBuilder();
        csv.append(csvVal(orgName)).append("\r\n");
        csv.append("Feedback report").append("\r\n");
        csv.append("Date range,").append(from == null ? "All" : from).append(" to ").append(to == null ? "All" : to).append("\r\n");
        csv.append("Generated,").append(generated).append(" (Manila)\r\n");
        csv.append("\r\n");
        csv.append("Submitted at,Type,Rating,Nickname,Therapist,OR#,Staff response,Comment\r\n");
        for (Feedback f : rows) {
            String therapistName = f.getTherapistId() == null ? "" : therapistRepository.findById(f.getTherapistId())
                    .map(t -> t.getNickname()).orElse("");
            csv.append(csvVal(f.getSubmittedAt() != null ? f.getSubmittedAt().format(fmt) : "")).append(',');
            csv.append(csvVal(f.getFeedbackType())).append(',');
            csv.append(f.getRatingStars()).append(',');
            csv.append(csvVal(f.getNickname())).append(',');
            csv.append(csvVal(therapistName)).append(',');
            csv.append(csvVal(f.getOrNumber())).append(',');
            csv.append(csvVal(f.getStaffResponse())).append(',');
            csv.append(csvVal(f.getComment())).append("\r\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"feedback.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    public record RespondRequest(@Size(max = 2000) String response) {}

    private String csvVal(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public record PublicFeedbackRequest(
            @Min(1) @Max(5) int ratingStars,
            String comment,
            @Size(max = 120) String nickname,
            @Size(max = 50) String orNumber
    ) {}
}
