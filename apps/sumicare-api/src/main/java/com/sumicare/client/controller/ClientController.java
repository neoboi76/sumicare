package com.sumicare.client.controller;

import com.sumicare.client.domain.Client;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
}
