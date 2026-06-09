package com.sumicare.auth.email;

import com.sumicare.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sumicare.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            boolean multipart = !message.inlineImages().isEmpty() || !message.attachments().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(mime, multipart, "UTF-8");
            helper.setFrom(appProperties.app().emailFrom());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
            for (InlineImage image : message.inlineImages()) {
                helper.addInline(image.contentId(), new ByteArrayDataSource(image.png(), "image/png"));
            }
            for (Attachment attachment : message.attachments()) {
                helper.addAttachment(attachment.filename(),
                        new ByteArrayDataSource(attachment.content(), attachment.contentType()));
            }
            mailSender.send(mime);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via SMTP", e);
        }
    }
}
