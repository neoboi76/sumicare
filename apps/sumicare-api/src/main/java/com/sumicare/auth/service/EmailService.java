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
        sendHtml(to, subject, body);
    }

    @Async
    public void sendPasswordResetEmail(String to, String displayName, String token) {
        String link = appProperties.app().publicBaseUrl() + "/reset-password?token=" + token;
        String subject = "Reset your SumiCare password";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #0F766E;">Password Reset Request</h2>
                  <p>Hello %s,</p>
                  <p>We received a request to reset your SumiCare account password. Click the button below to choose a new password.</p>
                  <p style="margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #0F766E; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">
                      Reset Password
                    </a>
                  </p>
                  <p>This link expires in 1 hour. If you did not request a password reset, you may safely ignore this email.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">SumiCare &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName, link);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendInvitationEmail(String to, String displayName, String token) {
        String link = appProperties.app().publicBaseUrl() + "/invite?token=" + token;
        String subject = "You have been invited to SumiCare";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Welcome to SumiCare</h2>
                  <p>Hello %s,</p>
                  <p>Your SumiCare account has been created. Click the button below to set your password and get started.</p>
                  <p style="margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #c42441; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">
                      Accept Invitation
                    </a>
                  </p>
                  <p>This link expires in 30 minutes. If you did not expect this email, you may safely ignore it.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">SumiCare &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName == null ? "there" : displayName, link);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendBookingConfirmationEmail(String to, String displayName, BookingEmailPayload payload) {
        String subject = "Your SumiCare booking is confirmed";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Thanks for booking with SumiCare</h2>
                  <p>Hello %s,</p>
                  <p>We have recorded your reservation. Here is a copy of your details:</p>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reference</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Package</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Massage</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reservation type</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Scheduled</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Effective start</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Room</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Total</td><td style="padding: 6px 12px;">&#8369; %s</td></tr>
                  </table>
                  <p style="margin-top: 24px;">Sessions begin 15 minutes after your scheduled time to allow room preparation. Please arrive a few minutes early.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">SumiCare &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(
                        displayName == null ? "there" : displayName,
                        payload.reference(),
                        payload.packageName() == null ? "(no package)" : payload.packageName(),
                        payload.serviceName() == null ? "(walk-in)" : payload.serviceName(),
                        payload.reservationType(),
                        payload.scheduled(),
                        payload.effectiveStart(),
                        payload.roomType(),
                        payload.total());
        sendHtml(to, subject, body);
    }

    public record BookingEmailPayload(
            String reference,
            String packageName,
            String serviceName,
            String reservationType,
            String scheduled,
            String effectiveStart,
            String roomType,
            String total) {}

    @Async
    public void sendAdminPasswordResetNotice(String to, String adminDisplayName, String requesterName, String requesterEmail, String message) {
        String subject = "Password reset request from " + (requesterName == null ? requesterEmail : requesterName);
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Password reset request</h2>
                  <p>Hello %s,</p>
                  <p>A SumiCare user has requested help with their password.</p>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Name</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Email</td><td style="padding: 6px 12px;">%s</td></tr>
                  </table>
                  <p style="margin-top: 16px;">Their message:</p>
                  <blockquote style="border-left: 3px solid #c42441; padding: 8px 16px; color: #1a1a1a;">%s</blockquote>
                  <p>Open the SumiCare admin Users page and use <strong>Send reset link</strong> on the matching row to email them a reset link.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">SumiCare &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(
                        adminDisplayName == null ? "Admin" : adminDisplayName,
                        requesterName == null ? "(unknown)" : requesterName,
                        requesterEmail == null ? "(unknown)" : requesterEmail,
                        message == null ? "(no message)" : message);
        sendHtml(to, subject, body);
    }

    private void sendHtml(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.app().emailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
