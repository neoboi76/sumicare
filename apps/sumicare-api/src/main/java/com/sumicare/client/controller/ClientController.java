package com.sumicare.client.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.client.domain.Client;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class ClientController {

    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;

    public ClientController(ClientRepository clientRepository, OrganizationRepository organizationRepository) {
        this.clientRepository = clientRepository;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/api/public/clients/{slug}")
    public Client register(@PathVariable String slug, @RequestBody Client request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        if (clientRepository.existsByOrganizationIdAndNickname(organizationId, request.getNickname())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nickname already taken");
        }
        request.setId(null);
        request.setOrganizationId(organizationId);
        return clientRepository.save(request);
    }

    @GetMapping("/api/public/clients/{slug}/check-nickname")
    public boolean isAvailable(@PathVariable String slug, @RequestParam String nickname) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        return !clientRepository.existsByOrganizationIdAndNickname(organizationId, nickname);
    }

    @GetMapping("/api/clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<Client> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                             @RequestParam(required = false) String q) {
        UUID orgId = UUID.fromString(principal.organizationId());
        if (q == null || q.isBlank()) {
            return clientRepository.findAllByOrganizationIdOrderByNicknameAsc(orgId);
        }
        return clientRepository.findTop20ByOrganizationIdAndNicknameContainingIgnoreCaseOrderByNicknameAsc(orgId, q);
    }

    @PostMapping("/api/clients")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Client createInternal(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @RequestBody Client request) {
        UUID orgId = UUID.fromString(principal.organizationId());
        if (request.getNickname() == null || request.getNickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname required");
        }
        if (clientRepository.existsByOrganizationIdAndNickname(orgId, request.getNickname())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nickname already taken");
        }
        request.setId(null);
        request.setOrganizationId(orgId);
        return clientRepository.save(request);
    }
}
