/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.dto.FeedbackEntryResponse;
import com.sumicare.feedback.dto.OrderFeedbackGroupResponse;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.report.service.ExcelExportService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class FeedbackController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final FeedbackRepository repository;
    private final OrganizationRepository organizationRepository;
    private final NotificationService notificationService;
    private final TherapistRepository therapistRepository;
    private final ExcelExportService excelExportService;
    private final ObjectMapper objectMapper;

    public FeedbackController(FeedbackRepository repository,
                              OrganizationRepository organizationRepository,
                              NotificationService notificationService,
                              TherapistRepository therapistRepository,
                              ExcelExportService excelExportService,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.notificationService = notificationService;
        this.therapistRepository = therapistRepository;
        this.excelExportService = excelExportService;
        this.objectMapper = objectMapper;
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

    @GetMapping("/api/feedback/by-order")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<OrderFeedbackGroupResponse> byOrder(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null
                ? LocalDate.parse(from).atStartOfDay(MANILA).toOffsetDateTime()
                : OffsetDateTime.now().minusYears(10);
        OffsetDateTime end = to != null
                ? LocalDate.parse(to).plusDays(1).atStartOfDay(MANILA).toOffsetDateTime()
                : OffsetDateTime.now().plusDays(1);
        List<Feedback> rows = repository.findAllByOrganizationIdAndSubmittedAtBetweenOrderByOrderIdAscSubmittedAtAsc(
                orgId, start, end);
        return groupByOrder(rows);
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

    @GetMapping("/api/feedback/export.xlsx")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportXlsx(
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
        String orgName = organizationRepository.findById(orgId)
                .map(o -> o.getDisplayName()).orElse("Organization");
        String logoUrl = organizationRepository.findById(orgId)
                .map(o -> o.getLogoUrl()).orElse(null);
        List<Feedback> rows = repository.findAllByOrganizationIdAndSubmittedAtBetweenOrderBySubmittedAtAsc(orgId, start, end)
                .stream()
                .filter(f -> type == null || type.isBlank() || type.equalsIgnoreCase(f.getFeedbackType()))
                .filter(f -> therapistId == null || therapistId.equals(f.getTherapistId()))
                .toList();
        String range = (from == null ? "All" : from) + " to " + (to == null ? "All" : to);
        ExcelExportService.WorkbookContext ctx = excelExportService.createWorkbook(
                "Feedback Report", "Feedback Report — " + orgName, range, null, logoUrl);
        excelExportService.writeHeaderRow(ctx, List.of(
                "Submitted At", "Type", "Rating", "Nickname", "Therapist", "OR#", "Staff Response", "Comment"));
        for (Feedback f : rows) {
            String therapistName = f.getTherapistId() == null ? "" : therapistRepository.findById(f.getTherapistId())
                    .map(t -> t.getNickname()).orElse("");
            excelExportService.writeDataRow(ctx, List.of(
                    f.getSubmittedAt() != null ? f.getSubmittedAt().atZoneSameInstant(MANILA).format(STAMP) : "",
                    f.getFeedbackType() != null ? f.getFeedbackType() : "",
                    f.getRatingStars(),
                    f.getNickname() != null ? f.getNickname() : "",
                    therapistName,
                    f.getOrNumber() != null ? f.getOrNumber() : "",
                    f.getStaffResponse() != null ? f.getStaffResponse() : "",
                    f.getComment() != null ? f.getComment() : ""));
        }
        excelExportService.writeFooter(ctx, 8);
        excelExportService.autoSizeColumns(ctx, 8);
        byte[] data = excelExportService.toBytes(ctx.workbook);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"feedback.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }

    private List<OrderFeedbackGroupResponse> groupByOrder(List<Feedback> rows) {
        Map<String, List<Feedback>> grouped = new LinkedHashMap<>();
        for (Feedback f : rows) {
            String key = f.getOrderId() != null ? f.getOrderId().toString() : ("none-" + f.getId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        List<OrderFeedbackGroupResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<Feedback>> entry : grouped.entrySet()) {
            List<Feedback> group = entry.getValue();
            Feedback first = group.get(0);
            UUID orderId = first.getOrderId();
            String orderRef = first.getOrNumber();
            String firstSubmittedAt = first.getSubmittedAt() != null
                    ? first.getSubmittedAt().atZoneSameInstant(MANILA).format(STAMP) : "";
            boolean hasSurvey = group.stream().anyMatch(f ->
                    "LASEMA".equals(f.getFeedbackType()) || "THERAPIST".equals(f.getFeedbackType()));
            List<FeedbackEntryResponse> entries = group.stream()
                    .map(this::toEntryResponse)
                    .toList();
            result.add(new OrderFeedbackGroupResponse(orderId, orderRef, firstSubmittedAt, hasSurvey, entries));
        }
        return result;
    }

    private FeedbackEntryResponse toEntryResponse(Feedback f) {
        String therapistNickname = f.getTherapistId() == null ? null
                : therapistRepository.findById(f.getTherapistId()).map(t -> t.getNickname()).orElse(null);
        Map<String, Integer> criteriaMap = parseCriteria(f.getCriteria());
        String submittedAt = f.getSubmittedAt() != null
                ? f.getSubmittedAt().atZoneSameInstant(MANILA).format(STAMP) : "";
        return new FeedbackEntryResponse(
                f.getId(), f.getFeedbackType(), f.getTherapistId(), therapistNickname,
                f.getRatingStars(), f.getNpsScore(), f.getComment(), criteriaMap,
                f.getStaffResponse(), submittedAt);
    }

    private Map<String, Integer> parseCriteria(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    public record RespondRequest(@Size(max = 2000) String response) {}

    public record PublicFeedbackRequest(
            @Min(1) @Max(5) int ratingStars,
            String comment,
            @Size(max = 120) String nickname,
            @Size(max = 50) String orNumber
    ) {}
}
