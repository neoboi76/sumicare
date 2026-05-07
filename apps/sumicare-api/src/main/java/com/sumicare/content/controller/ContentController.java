package com.sumicare.content.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.content.domain.WebsiteContentBlock;
import com.sumicare.content.repository.WebsiteContentBlockRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ContentController {

    private final WebsiteContentBlockRepository repository;
    private final OrganizationRepository organizationRepository;

    @Value("${sumicare.app.publicBaseUrl:http://localhost:8080}")
    private String publicBaseUrl;

    private static final String UPLOAD_DIR = "uploads";

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

    @PutMapping("/api/content/blocks/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public WebsiteContentBlock update(@PathVariable UUID id,
                                      @RequestBody WebsiteContentBlock update,
                                      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        WebsiteContentBlock block = repository.findById(id).orElseThrow();
        if (update.getTitle() != null) block.setTitle(update.getTitle());
        if (update.getBody() != null) block.setBody(update.getBody());
        if (update.getImageUrl() != null) block.setImageUrl(update.getImageUrl());
        if (update.getDisplayOrder() != null) block.setDisplayOrder(update.getDisplayOrder());
        block.setPublished(update.isPublished());
        block.setUpdatedAt(OffsetDateTime.now());
        return repository.save(block);
    }

    @PostMapping(value = "/api/content/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path dest = uploadPath.resolve(filename);
        file.transferTo(dest.toFile());
        String url = publicBaseUrl + "/uploads/" + filename;
        return Map.of("url", url);
    }
}
