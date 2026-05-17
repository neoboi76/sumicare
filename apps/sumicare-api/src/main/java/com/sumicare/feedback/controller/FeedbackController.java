package com.sumicare.feedback.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
public class FeedbackController {

    private final FeedbackRepository repository;
    private final OrganizationRepository organizationRepository;

    public FeedbackController(FeedbackRepository repository, OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/api/public/feedback/{slug}")
    public Feedback submit(@PathVariable String slug, @RequestBody PublicFeedbackRequest request) {
        UUID orgId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        Feedback feedback = new Feedback();
        feedback.setOrganizationId(orgId);
        feedback.setRatingStars(request.ratingStars());
        feedback.setComment(request.comment());
        return repository.save(feedback);
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

    @GetMapping(value = "/api/feedback/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null ? LocalDate.parse(from).atStartOfDay().atOffset(ZoneOffset.UTC) : OffsetDateTime.now().minusYears(10);
        OffsetDateTime end = to != null ? LocalDate.parse(to).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : OffsetDateTime.now().plusDays(1);
        List<Feedback> rows = repository.findAllByOrganizationIdAndSubmittedAtBetweenOrderBySubmittedAtAsc(orgId, start, end);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder csv = new StringBuilder("Submitted at,Rating,Session ID,Client ID,Comment\r\n");
        for (Feedback f : rows) {
            csv.append(csvVal(f.getSubmittedAt() != null ? f.getSubmittedAt().format(fmt) : "")).append(',');
            csv.append(f.getRatingStars()).append(',');
            csv.append(csvVal(f.getSessionId() != null ? f.getSessionId().toString() : "")).append(',');
            csv.append(csvVal(f.getClientId() != null ? f.getClientId().toString() : "")).append(',');
            csv.append(csvVal(f.getComment())).append("\r\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"feedback.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private String csvVal(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public record PublicFeedbackRequest(@Min(1) @Max(5) int ratingStars, String comment) {}
}
