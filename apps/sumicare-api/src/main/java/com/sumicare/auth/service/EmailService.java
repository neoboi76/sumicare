package com.sumicare.auth.service;

import com.sumicare.auth.email.Attachment;
import com.sumicare.auth.email.EmailMessage;
import com.sumicare.auth.email.EmailSender;
import com.sumicare.auth.email.InlineImage;
import com.sumicare.common.util.BaseUrlResolver;
import com.sumicare.common.util.QrCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSender emailSender;
    private final BaseUrlResolver baseUrlResolver;

    public EmailService(EmailSender emailSender, BaseUrlResolver baseUrlResolver) {
        this.emailSender = emailSender;
        this.baseUrlResolver = baseUrlResolver;
    }

    @Async
    public void sendVerificationEmail(String to, String displayName, String token) {
        String link = baseUrlResolver.resolve() + "/verify?token=" + token;
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
        String link = baseUrlResolver.resolve() + "/reset-password?token=" + token;
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
        String link = baseUrlResolver.resolve() + "/invite?token=" + token;
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

    public boolean sendCancellationCodeEmail(String to, String displayName, String code) {
        try {
            sendHtml(to, "Your New Lasema Spa Jjimjilbang cancellation code", cancellationCodeBody(displayName, code));
            return true;
        } catch (Exception e) {
            log.error("Cancellation code email failed for {}: {}", maskRecipient(to), e.getMessage());
            return false;
        }
    }

    private String cancellationCodeBody(String displayName, String code) {
        return """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Confirm your cancellation</h2>
                  <p>Hello %s,</p>
                  <p>We received a request to cancel your reservation. Enter the code below to verify your identity and review the booking before it is cancelled.</p>
                  <p style="font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #c42441; margin: 24px 0;">%s</p>
                  <p>This code expires in 15 minutes. If you did not request this, you may safely ignore this email and your reservation will remain unchanged.</p>
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName == null ? "there" : displayName, code);
    }

    private String maskRecipient(String to) {
        if (to == null) {
            return "(none)";
        }
        int at = to.indexOf('@');
        return at >= 0 ? "***" + to.substring(at) : "***";
    }

    @Async
    public void sendCancellationConfirmedEmail(String to, String displayName, String reference,
                                               List<String> services, BigDecimal refundAmount, boolean refunded) {
        String subject = "Your New Lasema Spa Jjimjilbang reservation has been cancelled";
        StringBuilder servicesHtml = new StringBuilder();
        if (services != null) {
            for (String line : services) {
                servicesHtml.append("<tr><td style=\"padding: 6px 12px;\">")
                        .append(line == null ? "" : line)
                        .append("</td></tr>");
            }
        }
        String servicesBlock = servicesHtml.length() == 0 ? ""
                : "<h3 style=\"margin-top: 24px; color: #1a1a1a;\">Cancelled services</h3>"
                + "<table style=\"border-collapse: collapse; margin-top: 8px; width: 100%;\">"
                + servicesHtml + "</table>";
        String refundLine = refunded
                ? "<p>A refund of <strong>&#8369; " + refundAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + "</strong> is being returned to your original payment method. Refunds may take a few business days to appear.</p>"
                : "";
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Reservation cancelled</h2>
                  <p>Hello %s,</p>
                  <p>Your reservation <strong>%s</strong> has been cancelled. We hope to welcome you another time.</p>
                  %s
                  %s
                  <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;" />
                  <p style="font-size: 12px; color: #6b7280;">New Lasema Spa Jjimjilbang &mdash; Spa Operations Management</p>
                </body>
                </html>
                """.formatted(displayName == null ? "there" : displayName, reference, servicesBlock, refundLine);
        sendHtml(to, subject, body);
    }

    @Async
    public void sendBookingConfirmationEmail(String to, String displayName, BookingEmailPayload payload) {
        String subject = "Your New Lasema Spa Jjimjilbang booking is confirmed";
        StringBuilder packagesHtml = new StringBuilder();
        if (payload.packages() != null) {
            for (PackageLine line : payload.packages()) {
                packagesHtml.append("<tr><td style=\"padding: 6px 12px; color: #6b7280; vertical-align: top;\">Package</td><td style=\"padding: 6px 12px;\"><div style=\"font-weight: 600;\">")
                        .append(line.name() == null ? "" : line.name())
                        .append("</div>");
                if (line.massages() != null && !line.massages().isBlank()) {
                    packagesHtml.append("<div style=\"font-size: 13px; color: #374151;\">Massage: ")
                            .append(line.massages()).append("</div>");
                }
                if (line.inclusions() != null && !line.inclusions().isEmpty()) {
                    packagesHtml.append("<div style=\"font-size: 12px; color: #6b7280;\">Inclusions: ")
                            .append(String.join(", ", line.inclusions())).append("</div>");
                }
                packagesHtml.append("</td></tr>");
            }
        }
        String body = """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h2 style="color: #c42441;">Thanks for booking with New Lasema Spa Jjimjilbang</h2>
                  <p>Hello %s,</p>
                  <p>We have recorded your reservation. Here is a copy of your details:</p>
                  <table style="border-collapse: collapse; margin-top: 16px;">
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reference</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">OR #</td><td style="padding: 6px 12px; font-family: monospace;">%s</td></tr>
                    %s
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Reservation type</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Scheduled</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Effective start</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Room</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Payment method</td><td style="padding: 6px 12px;">%s</td></tr>
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
                        packagesHtml.toString(),
                        payload.reservationType(),
                        payload.scheduled(),
                        payload.effectiveStart(),
                        payload.roomType(),
                        payload.paymentMethod() == null || payload.paymentMethod().isBlank() ? "Pending" : payload.paymentMethod(),
                        payload.total());
        sendHtml(to, subject, body);
    }

    public record PackageLine(String name, String massages, List<String> inclusions) {}

    public record BookingEmailPayload(
            String reference,
            String orNumber,
            List<PackageLine> packages,
            String reservationType,
            String scheduled,
            String effectiveStart,
            String roomType,
            String paymentMethod,
            String total) {}

    public record CompletionEmailPayload(
            String reference,
            String orNumber,
            List<String> availed,
            String scheduled,
            String effectiveStart,
            String total,
            String paymentMethod,
            List<String> slipLines) {}

    public record EmailAttachment(String filename, byte[] content) {}

    @Async
    public void sendCompletionEmail(String to, String displayName, CompletionEmailPayload payload,
                                    byte[] receiptPdf, List<EmailAttachment> slipPdfs) {
        String subject = "Thanks for Choosing New Lasema Spa Jjimjilbang";
        String orForLink = payload.orNumber() == null ? "" : payload.orNumber();
        String feedbackUrl = baseUrlResolver.resolve()
                + "/feedback?or=" + URLEncoder.encode(orForLink, StandardCharsets.UTF_8);
        StringBuilder availedRows = new StringBuilder();
        if (payload.availed() != null) {
            for (String line : payload.availed()) {
                availedRows.append("<tr><td style=\"padding: 6px 12px;\">")
                        .append(line == null ? "" : line)
                        .append("</td></tr>");
            }
        }
        StringBuilder slipRows = new StringBuilder();
        if (payload.slipLines() != null) {
            for (String line : payload.slipLines()) {
                slipRows.append("<tr><td style=\"padding: 6px 12px;\">")
                        .append(line == null ? "" : line)
                        .append("</td></tr>");
            }
        }
        String paymentMethodLabel = payload.paymentMethod() == null || payload.paymentMethod().isBlank()
                ? "" : payload.paymentMethod();
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
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Payment method</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Scheduled</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Effective start</td><td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; color: #6b7280;">Total</td><td style="padding: 6px 12px;">&#8369; %s</td></tr>
                  </table>
                  %s
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
                        paymentMethodLabel,
                        payload.scheduled() == null ? "" : payload.scheduled(),
                        payload.effectiveStart() == null ? "" : payload.effectiveStart(),
                        payload.total() == null ? "" : payload.total(),
                        slipRows.length() == 0 ? "" :
                                "<h3 style=\"margin-top: 24px; color: #1a1a1a;\">Treatment slips</h3>" +
                                "<table style=\"border-collapse: collapse; margin-top: 8px; width: 100%;\">" + slipRows + "</table>",
                        feedbackUrl,
                        feedbackUrl);
        sendHtmlWithAttachments(to, subject, body, feedbackUrl, receiptPdf, slipPdfs);
    }

    private void sendHtmlWithAttachments(String to, String subject, String body, String feedbackUrl,
                                         byte[] receiptPdf, List<EmailAttachment> slipPdfs) {
        List<InlineImage> inlineImages = List.of(
                new InlineImage("feedbackQr", "feedback-qr.png", QrCodeUtil.pngBytes(feedbackUrl, 160)));
        List<Attachment> attachments = new ArrayList<>();
        if (receiptPdf != null && receiptPdf.length > 0) {
            attachments.add(new Attachment("official-receipt.pdf", "application/pdf", receiptPdf));
        }
        if (slipPdfs != null) {
            for (EmailAttachment slip : slipPdfs) {
                if (slip.content() != null && slip.content().length > 0) {
                    attachments.add(new Attachment(slip.filename(), "application/pdf", slip.content()));
                }
            }
        }
        emailSender.send(new EmailMessage(to, subject, withPoweredBy(body), inlineImages, attachments));
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
        emailSender.send(EmailMessage.html(to, subject, withPoweredBy(body)));
    }

    private String withPoweredBy(String body) {
        String footer = "<p style=\"font-size: 11px; color: #9ca3af; text-align: center; margin-top: 16px;\">Powered by SumiCare</p>";
        if (body.contains("</body>")) {
            return body.replace("</body>", footer + "</body>");
        }
        return body + footer;
    }
}
