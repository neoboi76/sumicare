package com.sumicare.service_catalogue.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ServiceCatalogueController {

    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;

    public ServiceCatalogueController(ServiceRepository serviceRepository, OrganizationRepository organizationRepository) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/api/services")
    public List<Service> internal(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return serviceRepository.findAllByOrganizationIdAndActiveTrue(UUID.fromString(principal.organizationId()));
    }

    @GetMapping("/api/public/services/{slug}")
    public List<Service> publicCatalogue(@PathVariable String slug) {
        return organizationRepository.findBySlug(slug)
                .map(o -> serviceRepository.findAllByOrganizationIdAndActiveTrue(o.getId()))
                .orElseGet(List::of);
    }
}
