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
