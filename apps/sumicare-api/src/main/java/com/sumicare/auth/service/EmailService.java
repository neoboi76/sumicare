package com.sumicare.auth.service;

import com.sumicare.common.config.AppProperties;
import com.sumicare.common.util.QrCodeUtil;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        String subject = "Confirm your New Lasema Spa Jjimjilbang account";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #0F766E;">Welcome to New Lasema Spa Jjimjilbang</h2>
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
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName, link);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendPasswordResetEmail(String to, String displayName, String token) {
        String link = appProperties.app().publicBaseUrl() + "/reset-password?token=" + token;
        String subject = "Reset your New Lasema Spa Jjimjilbang password";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #0F766E;">Password Reset Request</h2>
                  <p>Hello %s,</p>
                  <p>We received a request to reset your New Lasema Spa Jjimjilbang account password. Click the button below to choose a new password.</p>
                  <p style="margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #0F766E; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">
                      Reset Password
                    </a>
                  </p>
                  <p>This link expires in 1 hour. If you did not request a password reset, you may safely ignore this email.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName, link);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendInvitationEmail(String to, String displayName, String token) {
        String link = appProperties.app().publicBaseUrl() + "/invite?token=" + token;
        String subject = "You have been invited to New Lasema Spa Jjimjilbang";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Welcome to New Lasema Spa Jjimjilbang</h2>
                  <p>Hello %s,</p>
                  <p>Your New Lasema Spa Jjimjilbang account has been created. Click the button below to set your password and get started.</p>
                  <p style="margin: 32px 0;">
                    <a href="%s"
                       style="background-color: #c42441; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">
                      Accept Invitation
                    </a>
                  </p>
                  <p>This link expires in 30 minutes. If you did not expect this email, you may safely ignore it.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName == null ? "there" : displayName, link);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendMfaCodeEmail(String to, String displayName, String code) {
        String subject = "Your New Lasema Spa Jjimjilbang verification code";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #0F766E;">Verify your sign-in</h2>
                  <p>Hello %s,</p>
                  <p>Use the verification code below to finish signing in to your New Lasema Spa Jjimjilbang account.</p>
                  <p style="font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #0F766E; margin: 24px 0;">%s</p>
                  <p>This code expires in 15 minutes. If you did not try to sign in, please change your password and contact an administrator.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName == null ? "there" : displayName, code);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendBookingConfirmationEmail(String to, String displayName, BookingEmailPayload payload) {
        String subject = "Your New Lasema Spa Jjimjilbang booking is confirmed";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Thanks for booking with New Lasema Spa Jjimjilbang</h2>
                  <p>Hello %s,</p>
                  <p>We have recorded your reservation. Here is a copy of your details:</p>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reference</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">OR #</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
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
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(
                        displayName == null ? "there" : displayName,
                        payload.reference(),
                        payload.orNumber() == null || payload.orNumber().isBlank() ? "To be issued" : payload.orNumber(),
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
            String orNumber,
            String packageName,
            String serviceName,
            String reservationType,
            String scheduled,
            String effectiveStart,
            String roomType,
            String total) {}

    public record CompletionEmailPayload(
            String reference,
            String orNumber,
            List<String> availed,
            String scheduled,
            String effectiveStart,
            String total) {}

    public record EmailAttachment(String filename, byte[] content) {}

    @Async
    public void sendCompletionEmail(String to, String displayName, CompletionEmailPayload payload,
                                    byte[] receiptPdf, List<EmailAttachment> slipPdfs) {
        String subject = "Thanks for Choosing New Lasema Spa Jjimjilbang";
        String orForLink = payload.orNumber() == null ? "" : payload.orNumber();
        String feedbackUrl = appProperties.app().publicBaseUrl()
                + "/feedback?or=" + URLEncoder.encode(orForLink, StandardCharsets.UTF_8);
        StringBuilder availedRows = new StringBuilder();
        if (payload.availed() != null) {
            for (String line : payload.availed()) {
                availedRows.append("<tr><td style=\"padding: 6px 12px;\">")
                        .append(line == null ? "" : line)
                        .append("</td></tr>");
            }
        }
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Thanks for Choosing New Lasema Spa Jjimjilbang</h2>
                  <p>Hello %s,</p>
                  <p>Your visit is complete and fully settled. Here is a summary of what you availed:</p>
                  <table style="border-collapse: collapse; margin-top: 8px; width: 100%%;">%s</table>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reference</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">OR #</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Scheduled</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Effective start</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Total</td><td style="padding: 6px 12px;">&#8369; %s</td></tr>
                  </table>
                  <p style="margin-top: 24px;">A copy of your official receipt and treatment slips are attached. We would love your feedback &mdash; scan the code below:</p>
                  <p style="margin: 8px 0;"><img src="cid:feedbackQr" alt="Feedback QR code" style="width: 160px; height: 160px;" /></p>
                  <p style="font-size: 12px;"><a href="%s" style="color: #0F766E;">%s</a></p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                  <p style="font-size: 11px; color: #9ca3af;">This email has been sent from an automated system. Please do not reply to it.</p>
                </body>
                </html>
                """.formatted(
                        displayName == null ? "there" : displayName,
                        availedRows.toString(),
                        payload.reference(),
                        payload.orNumber() == null || payload.orNumber().isBlank() ? "To be issued" : payload.orNumber(),
                        payload.scheduled() == null ? "" : payload.scheduled(),
                        payload.effectiveStart() == null ? "" : payload.effectiveStart(),
                        payload.total() == null ? "" : payload.total(),
                        feedbackUrl,
                        feedbackUrl);
        sendHtmlWithAttachments(to, subject, body, feedbackUrl, receiptPdf, slipPdfs);
    }

    private void sendHtmlWithAttachments(String to, String subject, String body, String feedbackUrl,
                                         byte[] receiptPdf, List<EmailAttachment> slipPdfs) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.app().emailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(withPoweredBy(body), true);
            helper.addInline("feedbackQr", new ByteArrayDataSource(QrCodeUtil.pngBytes(feedbackUrl, 160), "image/png"));
            if (receiptPdf != null && receiptPdf.length > 0) {
                helper.addAttachment("official-receipt.pdf", new ByteArrayDataSource(receiptPdf, "application/pdf"));
            }
            if (slipPdfs != null) {
                for (EmailAttachment slip : slipPdfs) {
                    if (slip.content() != null && slip.content().length > 0) {
                        helper.addAttachment(slip.filename(), new ByteArrayDataSource(slip.content(), "application/pdf"));
                    }
                }
            }
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send completion email", e);
        }
    }

    @Async
    public void sendAdminPasswordResetNotice(String to, String adminDisplayName, String requesterName, String requesterEmail, String message) {
        String subject = "Password reset request from " + (requesterName == null ? requesterEmail : requesterName);
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Password reset request</h2>
                  <p>Hello %s,</p>
                  <p>A New Lasema Spa Jjimjilbang user has requested help with their password.</p>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Name</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Email</td><td style="padding: 6px 12px;">%s</td></tr>
                  </table>
                  <p style="margin-top: 16px;">Their message:</p>
                  <blockquote style="border-left: 3px solid #c42441; padding: 8px 16px; color: #1a1a1a;">%s</blockquote>
                  <p>Open the New Lasema Spa Jjimjilbang admin Users page and use <strong>Send reset link</strong> on the matching row to email them a reset link.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
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
            helper.setText(withPoweredBy(body), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String withPoweredBy(String body) {
        String footer = "<p style=\"font-size: 11px; color: #9ca3af; text-align: center; margin-top: 16px;\">Powered by SumiCare</p>";
        if (body.contains("</body>")) {
            return body.replace("</body>", footer + "</body>");
        }
        return body + footer;
    }
}
