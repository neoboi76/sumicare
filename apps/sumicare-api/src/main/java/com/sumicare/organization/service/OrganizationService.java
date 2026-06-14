package com.sumicare.organization.service;

import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.dto.OrganizationBrandingResponse;
import com.sumicare.organization.dto.UpdateBrandingRequest;
import com.sumicare.organization.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public OrganizationBrandingResponse getBranding(UUID organizationId) {
        Organization org = repository.findById(organizationId).orElseThrow();
        return toResponse(org);
    }

    public OrganizationBrandingResponse getBrandingBySlug(String slug) {
        Organization org = repository.findBySlug(slug).orElseThrow();
        return toResponse(org);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public OrganizationBrandingResponse updateBranding(UUID organizationId, UpdateBrandingRequest request) {
        Organization org = repository.findById(organizationId).orElseThrow();
        if (request.displayName() != null) org.setDisplayName(request.displayName());
        if (request.logoUrl() != null) org.setLogoUrl(request.logoUrl());
        if (request.primaryColor() != null) org.setPrimaryColor(request.primaryColor());
        if (request.secondaryColor() != null) org.setSecondaryColor(request.secondaryColor());
        if (request.accentColor() != null) org.setAccentColor(request.accentColor());
        if (request.theme() != null) org.setTheme(request.theme());
        if (request.fontFamily() != null) org.setFontFamily(request.fontFamily());
        if (request.loginBackgroundUrl() != null) org.setLoginBackgroundUrl(emptyToNull(request.loginBackgroundUrl()));
        if (request.faviconUrl() != null) org.setFaviconUrl(emptyToNull(request.faviconUrl()));
        if (request.instagramUrl() != null) org.setInstagramUrl(emptyToNull(request.instagramUrl()));
        if (request.contactPhone() != null) org.setContactPhone(emptyToNull(request.contactPhone()));
        if (request.contactEmail() != null) org.setContactEmail(emptyToNull(request.contactEmail()));
        if (request.footerNote() != null) org.setFooterNote(emptyToNull(request.footerNote()));
        org.setUpdatedAt(OffsetDateTime.now());
        return toResponse(org);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OrganizationBrandingResponse toResponse(Organization org) {
        return new OrganizationBrandingResponse(
                org.getId(), org.getSlug(), org.getDisplayName(),
                org.getLogoUrl(), org.getPrimaryColor(), org.getSecondaryColor(),
                org.getAccentColor(), org.getTheme(), org.getFontFamily(),
                org.getLoginBackgroundUrl(), org.getFaviconUrl(), org.getInstagramUrl(),
                org.getContactPhone(), org.getContactEmail(), org.getFooterNote()
        );
    }
}
