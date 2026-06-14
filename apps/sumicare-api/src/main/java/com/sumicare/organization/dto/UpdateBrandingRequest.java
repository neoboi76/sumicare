package com.sumicare.organization.dto;

public record UpdateBrandingRequest(
        String displayName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String theme,
        String fontFamily,
        String loginBackgroundUrl,
        String faviconUrl,
        String instagramUrl,
        String contactPhone,
        String contactEmail,
        String footerNote
) {}
