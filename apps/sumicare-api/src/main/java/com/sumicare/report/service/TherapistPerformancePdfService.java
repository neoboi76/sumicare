/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.common.util.LogoResolver;
import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.print.PdfRenderer;
import com.sumicare.feedback.service.SurveyAnalyticsService.TherapistSatisfactionStats;
import com.sumicare.report.service.TherapistPerformanceService.TherapistPerformance;
import com.sumicare.report.service.TherapistPerformanceService.TherapistPerformanceReport;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class TherapistPerformancePdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TherapistPerformanceService therapistPerformanceService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;
    private final GeminiNarrativeService geminiNarrativeService;

    public TherapistPerformancePdfService(TherapistPerformanceService therapistPerformanceService,
                                          OrganizationRepository organizationRepository,
                                          UserRepository userRepository,
                                          PdfRenderer pdfRenderer,
                                          LogoResolver logoResolver,
                                          GeminiNarrativeService geminiNarrativeService) {
        this.therapistPerformanceService = therapistPerformanceService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.pdfRenderer = pdfRenderer;
        this.logoResolver = logoResolver;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] generate(UUID organizationId, UUID preparedByUserId, UUID therapistId,
                           LocalDate from, LocalDate to) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        TherapistPerformanceReport fullReport = therapistPerformanceService.report(organizationId, from, to);
        TherapistPerformance perf = fullReport.therapists().stream()
                .filter(t -> t.therapistId().equals(therapistId))
                .findFirst()
                .orElse(null);

        if (perf == null) {
            return pdfRenderer.renderHtml(noDataHtml(org, preparedBy, from, to));
        }

        String reportContext = buildReportContext(perf, from, to);
        String narrative = geminiNarrativeService.generateInterpretation(reportContext);

        String html = buildHtml(org, perf, preparedBy, from, to, narrative);
        return pdfRenderer.renderHtml(html);
    }

    private String buildHtml(Organization org, TherapistPerformance perf,
                              String preparedBy, LocalDate from, LocalDate to, String narrative) {
        String logoDataUri = logoResolver.dataUriOrNull(org.getLogoUrl());
        String logoHtml = logoDataUri != null
                ? "<img src=\"" + logoDataUri + "\" style=\"max-height:48px;\" />"
                : "<div style=\"font-size:18px;font-weight:700;color:#c42441;\">" + escape(org.getDisplayName()) + "</div>";
        String range = DAY.format(from.atStartOfDay(MANILA)) + " to " + DAY.format(to.atStartOfDay(MANILA));
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);

        StringBuilder topClients = new StringBuilder();
        for (TherapistPerformanceService.NameCount nc : perf.topClients()) {
            topClients.append("<tr><td>").append(escape(nc.name())).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(nc.count()).append("</td></tr>");
        }

        StringBuilder topServices = new StringBuilder();
        for (TherapistPerformanceService.NameCount nc : perf.topServices()) {
            topServices.append("<tr><td>").append(escape(nc.name())).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(nc.count()).append("</td></tr>");
        }

        StringBuilder criteriaRows = new StringBuilder();
        for (Map.Entry<String, Double> e : perf.perCriterionAverages().entrySet()) {
            criteriaRows.append("<tr><td>").append(escape(humanizeKey(e.getKey()))).append("</td>")
                    .append("<td style=\"text-align:right;\">")
                    .append(String.format("%.2f", e.getValue())).append(" / 5</td></tr>");
        }

        StringBuilder commentsHtml = new StringBuilder();
        for (String comment : perf.recentComments()) {
            commentsHtml.append("<p style=\"font-style:italic;color:#374151;margin:4px 0;\">\u201C")
                    .append(escape(comment)).append("\u201D</p>");
        }

        return """
                <html>
                <head><style>
                  @page { size: A4 portrait; margin: 18mm 14mm; }
                  body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1a1a1a; font-size: 11px; }
                  h2 { font-size: 13px; color: #1e406e; margin: 18px 0 6px 0; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 5px 7px; }
                  th { background: #f8fafc; text-align: left; font-weight: 700; }
                  .kv-label { font-size: 9px; text-transform: uppercase; color: #6b7280; letter-spacing: .05em; }
                  .kv-value { font-size: 14px; font-weight: 700; color: #1a1a1a; }
                  .narrative { background: #f8fafc; border-left: 3px solid #1e406e; padding: 10px 14px; margin-top: 10px; line-height: 1.6; }
                  .footer { margin-top: 28px; font-size: 9px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <table style="width:100%%;border:none;margin:0 0 8px 0;"><tr>
                    <td style="border:none;padding:0;">%s</td>
                    <td style="border:none;padding:0;text-align:right;font-size:11px;color:#6b7280;">
                      <div style="font-size:15px;font-weight:700;color:#1a1a1a;">Therapist Performance Report</div>
                      <div>%s &mdash; %s</div>
                    </td>
                  </tr></table>
                  <h2>%s</h2>
                  <table style="width:100%%;border:none;margin:8px 0;"><tr>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Revenue</div><div class="kv-value">%s</div></td>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Commissions</div><div class="kv-value">%s</div></td>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Tips</div><div class="kv-value">%s</div></td>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Services</div><div class="kv-value">%d</div></td>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Requests</div><div class="kv-value">%d</div></td>
                    <td style="border:none;padding:4px 12px 4px 0;vertical-align:top;"><div class="kv-label">Avg Rating</div><div class="kv-value">%.2f / 5</div></td>
                    <td style="border:none;padding:4px 0;vertical-align:top;"><div class="kv-label">Satisfaction Index</div><div class="kv-value">%.1f%%</div></td>
                  </tr></table>
                  %s
                  %s
                  %s
                  <h2>Financial Interpretation</h2>
                  <div class="narrative">%s</div>
                  <div style="margin-top:18px;font-size:10px;color:#374151;">
                    <div>Prepared By: %s</div>
                    <div>Generated: %s (Manila)</div>
                  </div>
                  <div class="footer">Powered by SumiCare</div>
                </body>
                </html>
                """.formatted(
                logoHtml, escape(perf.nickname()), range,
                escape(perf.nickname()),
                peso(perf.revenue()), peso(perf.commissions()), peso(perf.tips()),
                perf.servicesRendered(), perf.specificRequests(),
                perf.averageSatisfactionRating(), perf.satisfactionIndex(),
                criteriaRows.isEmpty() ? "" : "<h2>Client Satisfaction Criteria</h2><table><thead><tr><th>Criterion</th><th style=\"text-align:right;\">Avg Score</th></tr></thead><tbody>" + criteriaRows + "</tbody></table>",
                commentsHtml.isEmpty() ? "" : "<h2>Recent Client Comments</h2>" + commentsHtml,
                buildSideBySideTables(topClients.toString(), topServices.toString()),
                escape(narrative != null ? narrative : ""),
                escape(preparedBy), generated
        );
    }

    private String buildSideBySideTables(String clientRows, String serviceRows) {
        if (clientRows.isBlank() && serviceRows.isBlank()) return "";
        return "<h2>Top Clients &amp; Services</h2>"
                + "<table><thead><tr>"
                + "<th>Top Clients</th><th style=\"text-align:right;\">Visits</th>"
                + "<th style=\"width:24px;\"></th>"
                + "<th>Top Services</th><th style=\"text-align:right;\">Count</th>"
                + "</tr></thead><tbody>"
                + clientRows + serviceRows
                + "</tbody></table>";
    }

    private String noDataHtml(Organization org, String preparedBy, LocalDate from, LocalDate to) {
        return "<html><body style=\"font-family:Arial;padding:40px;\">"
                + "<h2>Therapist Performance Report</h2>"
                + "<p>No activity recorded for the selected therapist between "
                + DAY.format(from.atStartOfDay(MANILA)) + " and " + DAY.format(to.atStartOfDay(MANILA)) + ".</p>"
                + "<p>Prepared By: " + escape(preparedBy) + "</p>"
                + "<p style=\"color:#9ca3af;\">Powered by SumiCare</p>"
                + "</body></html>";
    }

    private String buildReportContext(TherapistPerformance perf, LocalDate from, LocalDate to) {
        return String.format(
                "Therapist: %s. Period: %s to %s. Revenue: %s. Commissions: %s. Tips: %s. "
                + "Services rendered: %d. Specific requests: %d. "
                + "Average satisfaction rating: %.2f out of 5. Satisfaction index: %.1f%%.",
                perf.nickname(), from, to,
                perf.revenue().toPlainString(), perf.commissions().toPlainString(), perf.tips().toPlainString(),
                perf.servicesRendered(), perf.specificRequests(),
                perf.averageSatisfactionRating(), perf.satisfactionIndex()
        );
    }

    private String humanizeKey(String key) {
        if (key == null) return "";
        String spaced = key.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String peso(BigDecimal value) {
        return "&#8369; " + (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
