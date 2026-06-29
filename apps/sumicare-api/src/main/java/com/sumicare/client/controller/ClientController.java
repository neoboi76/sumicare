/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.client.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.client.domain.Client;
import com.sumicare.client.dto.ClientUsageResponse;
import com.sumicare.client.dto.UpdateClientRequest;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.client.service.ClientUsageService;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class ClientController {

    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final ClientUsageService clientUsageService;

    public ClientController(ClientRepository clientRepository,
                            OrganizationRepository organizationRepository,
                            ClientUsageService clientUsageService) {
        this.clientRepository = clientRepository;
        this.organizationRepository = organizationRepository;
        this.clientUsageService = clientUsageService;
    }

    @PostMapping("/api/public/clients/{slug}")
    public Client register(@PathVariable String slug, @RequestBody Client request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        if (request.getNickname() == null || request.getNickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email required");
        }
        if (clientRepository.existsByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNull(organizationId, request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered. Use a different email.");
        }
        request.setId(null);
        request.setOrganizationId(organizationId);
        request.setDeletedAt(null);
        return clientRepository.save(request);
    }

    @GetMapping("/api/public/clients/{slug}/check-email")
    public boolean isEmailAvailable(@PathVariable String slug, @RequestParam String email) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        return !clientRepository.existsByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNull(organizationId, email);
    }

    @GetMapping("/api/clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<Client> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                             @RequestParam(required = false) String q) {
        UUID orgId = UUID.fromString(principal.organizationId());
        if (q == null || q.isBlank()) {
            return clientRepository.findAllByOrganizationIdAndDeletedAtIsNullOrderByNicknameAsc(orgId);
        }
        return clientRepository.findTop20ByOrganizationIdAndNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(orgId, q);
    }

    @PostMapping("/api/clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Client createInternal(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @RequestBody Client request) {
        UUID orgId = UUID.fromString(principal.organizationId());
        if (request.getNickname() == null || request.getNickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email required");
        }
        if (clientRepository.existsByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNull(orgId, request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered. Use a different email.");
        }
        request.setId(null);
        request.setOrganizationId(orgId);
        request.setDeletedAt(null);
        return clientRepository.save(request);
    }

    @GetMapping("/api/clients/{id}/usage")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ClientUsageResponse usage(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @PathVariable UUID id) {
        return clientUsageService.forClient(UUID.fromString(principal.organizationId()), id);
    }

    @PatchMapping("/api/clients/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Client update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                         @PathVariable UUID id,
                         @Valid @RequestBody UpdateClientRequest request) {
        UUID orgId = UUID.fromString(principal.organizationId());
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!client.getOrganizationId().equals(orgId) || client.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (request.nickname() != null && !request.nickname().isBlank()) {
            client.setNickname(request.nickname().trim());
        }
        if (request.gender() != null) {
            client.setGender(request.gender());
        }
        if (request.nationality() != null) {
            client.setNationality(request.nationality());
        }
        return clientRepository.save(client);
    }

    @DeleteMapping("/api/clients/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                       @PathVariable UUID id) {
        UUID orgId = UUID.fromString(principal.organizationId());
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!client.getOrganizationId().equals(orgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (client.getDeletedAt() == null) {
            client.setDeletedAt(OffsetDateTime.now());
            clientRepository.save(client);
        }
        return ResponseEntity.noContent().build();
    }
}
