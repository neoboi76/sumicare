package com.sumicare.feedback.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.feedback.domain.Feedback;
import com.sumicare.feedback.repository.FeedbackRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    public record PublicFeedbackRequest(@Min(1) @Max(5) int ratingStars, String comment) {}
}
