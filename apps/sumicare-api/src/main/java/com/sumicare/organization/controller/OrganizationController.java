/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.organization.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.organization.dto.OrganizationBrandingResponse;
import com.sumicare.organization.dto.UpdateBrandingRequest;
import com.sumicare.organization.service.OrganizationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/api/public/branding/{slug}")
    public OrganizationBrandingResponse publicBranding(@PathVariable String slug) {
        return service.getBrandingBySlug(slug);
    }

    @GetMapping("/api/organization/branding")
    public OrganizationBrandingResponse currentBranding(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return service.getBranding(UUID.fromString(principal.organizationId()));
    }

    @PutMapping("/api/organization/branding")
    public OrganizationBrandingResponse updateBranding(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                       @RequestBody UpdateBrandingRequest request) {
        return service.updateBranding(UUID.fromString(principal.organizationId()), request);
    }
}
