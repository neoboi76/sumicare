/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.service_catalogue.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/services")
public class ServiceAdminController {

    private final ServiceRepository serviceRepository;

    public ServiceAdminController(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Service create(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody Service request) {
        request.setOrganizationId(UUID.fromString(principal.organizationId()));
        request.setActive(true);
        return serviceRepository.save(request);
    }

    @PatchMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Service update(@PathVariable Long serviceId, @RequestBody Service updates) {
        Service service = serviceRepository.findById(serviceId).orElseThrow();
        if (updates.getName() != null) service.setName(updates.getName());
        if (updates.getCode() != null) service.setCode(updates.getCode());
        if (updates.getDurationMinutes() > 0) service.setDurationMinutes(updates.getDurationMinutes());
        if (updates.getPrice() != null) service.setPrice(updates.getPrice());
        if (updates.getCommissionAmount() != null) service.setCommissionAmount(updates.getCommissionAmount());
        if (updates.getCategory() != null) service.setCategory(updates.getCategory());
        service.setRequiresTwoTherapists(updates.isRequiresTwoTherapists());
        service.setFixedRate(updates.isFixedRate());
        if (updates.getDescription() != null) service.setDescription(updates.getDescription());
        if (updates.getImageUrl() != null) service.setImageUrl(updates.getImageUrl());
        return service;
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivate(@PathVariable Long serviceId) {
        Service service = serviceRepository.findById(serviceId).orElseThrow();
        service.setActive(false);
    }
}
