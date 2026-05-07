package com.sumicare.auth.service;

import com.sumicare.common.config.AppProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public EmailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Async
    public void sendVerificationEmail(String to, String displayName, String token) {
        String link = appProperties.app().publicBaseUrl() + "/verify?token=" + token;
        String subject = "Confirm your SumiCare account";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #0F766E;">Welcome to SumiCare</h2>
                  <p>Hello %s,</p>
                  <p>Your account has been created. Click the button below to verify your email address and activate your account.</p>
                  <p style="margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #0F766E; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">
                      Verify Account
                    </a>
                  </p>
                  <p>This link expires in 24 hours. If you did not expect this email, you may safely ignore it.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">SumiCare &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName, link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.app().emailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}
