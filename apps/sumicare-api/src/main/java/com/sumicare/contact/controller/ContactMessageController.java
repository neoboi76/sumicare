package com.sumicare.contact.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.contact.domain.ContactMessage;
import com.sumicare.contact.dto.ContactMessageRequest;
import com.sumicare.contact.repository.ContactMessageRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ContactMessageController {

    private final ContactMessageRepository repository;
    private final OrganizationRepository organizationRepository;

    public ContactMessageController(ContactMessageRepository repository,
                                    OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/api/public/contact/{slug}")
    public ResponseEntity<Map<String, String>> submit(@PathVariable String slug,
                                                       @Valid @RequestBody ContactMessageRequest request,
                                                       HttpServletRequest httpRequest) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        String ip = clientIp(httpRequest);
        OffsetDateTime hourAgo = OffsetDateTime.now().minusHours(1);
        if (ip != null && repository.countByIpAddressAndCreatedAtAfter(ip, hourAgo) >= 5) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many messages from this address. Please try again later."));
        }
        ContactMessage msg = new ContactMessage();
        msg.setOrganizationId(organizationId);
        msg.setName(request.name().trim());
        msg.setEmail(request.email().trim());
        msg.setMessage(request.message().trim());
        msg.setIpAddress(ip);
        repository.save(msg);
        return ResponseEntity.ok(Map.of("message", "Thank you. We have received your message."));
    }

    @GetMapping("/api/contact-messages")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<ContactMessage> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @RequestParam(required = false, defaultValue = "false") boolean unread) {
        UUID orgId = UUID.fromString(principal.organizationId());
        return unread
                ? repository.findAllByOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(orgId)
                : repository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    @GetMapping("/api/contact-messages/unread-count")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return Map.of("count", repository.countByOrganizationIdAndReadAtIsNull(
                UUID.fromString(principal.organizationId())));
    }

    @PostMapping("/api/contact-messages/{id}/mark-read")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ContactMessage markRead(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @PathVariable UUID id) {
        UUID orgId = UUID.fromString(principal.organizationId());
        ContactMessage msg = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!msg.getOrganizationId().equals(orgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (msg.getReadAt() == null) {
            msg.setReadAt(OffsetDateTime.now());
            msg.setReadByUserId(UUID.fromString(principal.userId()));
        }
        return repository.save(msg);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
