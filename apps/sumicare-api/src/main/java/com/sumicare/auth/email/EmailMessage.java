/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.auth.email;

import java.util.List;

public record EmailMessage(
        String to,
        String subject,
        String html,
        List<InlineImage> inlineImages,
        List<Attachment> attachments
) {
    public static EmailMessage html(String to, String subject, String html) {
        return new EmailMessage(to, subject, html, List.of(), List.of());
    }
}
