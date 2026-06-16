/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
