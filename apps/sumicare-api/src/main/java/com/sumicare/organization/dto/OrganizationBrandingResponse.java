package com.sumicare.organization.dto;

import java.util.UUID;

public record OrganizationBrandingResponse(
        UUID id,
        String slug,
        String displayName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String theme
) {}
