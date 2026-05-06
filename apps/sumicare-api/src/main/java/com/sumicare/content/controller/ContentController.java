package com.sumicare.content.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.content.domain.WebsiteContentBlock;
import com.sumicare.content.repository.WebsiteContentBlockRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class ContentController {

    private final WebsiteContentBlockRepository repository;
    private final OrganizationRepository organizationRepository;

    public ContentController(WebsiteContentBlockRepository repository, OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/api/public/content/{slug}")
    public List<WebsiteContentBlock> publicContent(@PathVariable String slug) {
        return organizationRepository.findBySlug(slug)
                .map(o -> repository.findAllByOrganizationIdAndPublishedTrueOrderByDisplayOrderAsc(o.getId()))
                .orElseGet(List::of);
    }

    @GetMapping("/api/content")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<WebsiteContentBlock> internalContent(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return repository.findAllByOrganizationIdOrderByDisplayOrderAsc(UUID.fromString(principal.organizationId()));
    }

    @PostMapping("/api/content")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public WebsiteContentBlock save(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestBody WebsiteContentBlock block) {
        block.setOrganizationId(UUID.fromString(principal.organizationId()));
        block.setUpdatedAt(OffsetDateTime.now());
        return repository.save(block);
    }
}
