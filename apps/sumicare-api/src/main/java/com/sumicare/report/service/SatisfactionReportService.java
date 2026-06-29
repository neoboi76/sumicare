/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.common.util.LogoResolver;
import com.sumicare.feedback.service.SurveyAnalyticsService;
import com.sumicare.feedback.service.SurveyAnalyticsService.LasemaSatisfactionStats;
import com.sumicare.feedback.service.SurveyAnalyticsService.NpsResult;
import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.print.PdfRenderer;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class SatisfactionReportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final SurveyAnalyticsService surveyAnalyticsService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;
    private final GeminiNarrativeService geminiNarrativeService;

    public SatisfactionReportService(SurveyAnalyticsService surveyAnalyticsService,
                                     OrganizationRepository organizationRepository,
                                     UserRepository userRepository,
                                     PdfRenderer pdfRenderer,
                                     LogoResolver logoResolver,
                                     GeminiNarrativeService geminiNarrativeService) {
        this.surveyAnalyticsService = surveyAnalyticsService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.pdfRenderer = pdfRenderer;
        this.logoResolver = logoResolver;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public LasemaSatisfactionStats stats(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        return surveyAnalyticsService.lasemaStats(organizationId, from, to);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] pdf(UUID organizationId, UUID preparedByUserId, OffsetDateTime from, OffsetDateTime to) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        LasemaSatisfactionStats stats = surveyAnalyticsService.lasemaStats(organizationId, from, to);
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);
        String range = DAY.format(from.atZoneSameInstant(MANILA))
                + " to " + DAY.format(to.atZoneSameInstant(MANILA));

        String context = buildContext(stats, range);
        String narrative = geminiNarrativeService.generateInterpretation(context);

        String logoDataUri = logoResolver.dataUriOrNull(org.getLogoUrl());
        String logoHtml = logoDataUri != null
                ? "<img src=\"" + logoDataUri + "\" style=\"max-height:48px;\" />"
                : "<div style=\"font-size:18px;font-weight:700;color:#c42441;\">" + escape(org.getDisplayName()) + "</div>";

        String html = buildHtml(logoHtml, range, stats, narrative, preparedBy, generated);
        return pdfRenderer.renderHtml(html);
    }

    private String buildHtml(String logoHtml, String range, LasemaSatisfactionStats stats,
                              String narrative, String preparedBy, String generated) {
        NpsResult nps = stats.nps();

        StringBuilder distributionRows = new StringBuilder();
        for (int star = 5; star >= 1; star--) {
            long count = stats.overallDistribution().counts().getOrDefault(star, 0L);
            long total = stats.overallDistribution().total();
            double pct = total == 0 ? 0 : (double) count / total * 100.0;
            distributionRows.append("<tr>")
                    .append("<td>").append(star).append(" star").append(star == 1 ? "" : "s").append("</td>")
                    .append("<td style=\"text-align:right;\">").append(count).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(String.format("%.1f%%", pct)).append("</td>")
                    .append("</tr>");
        }

        StringBuilder criteriaRows = new StringBuilder();
        for (Map.Entry<String, Double> e : stats.perCriterionAverages().entrySet()) {
            criteriaRows.append("<tr>")
                    .append("<td>").append(escape(humanizeKey(e.getKey()))).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(String.format("%.2f / 5", e.getValue())).append("</td>")
                    .append("</tr>");
        }

        StringBuilder commentsHtml = new StringBuilder();
        for (String c : stats.recentComments()) {
            commentsHtml.append("<p style=\"font-style:italic;color:#374151;margin:4px 0;\">\u201C")
                    .append(escape(c)).append("\u201D</p>");
        }

        return """
                <html>
                <head><style>
                  @page { size: A4 portrait; margin: 18mm 14mm; }
                  body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1a1a1a; font-size: 11px; }
                  h2 { font-size: 13px; color: #1e406e; margin: 18px 0 6px 0; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 5px 8px; }
                  th { background: #f8fafc; text-align: left; font-weight: 700; }
                  .narrative { background: #f8fafc; border-left: 3px solid #1e406e; padding: 10px 14px; margin-top: 10px; line-height: 1.6; }
                  .footer { margin-top: 28px; font-size: 9px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <table style="width:100%%;border:none;margin:0 0 8px 0;"><tr>
                    <td style="border:none;padding:0;">%s</td>
                    <td style="border:none;padding:0;text-align:right;font-size:11px;color:#6b7280;">
                      <div style="font-size:15px;font-weight:700;color:#1a1a1a;">Overall Lasema Satisfaction Report</div>
                      <div>%s</div>
                    </td>
                  </tr></table>
                  <h2>Overall Rating Distribution</h2>
                  <p style="font-size:12px;">Average: <strong>%.2f / 5</strong> from <strong>%d</strong> responses.
                  Satisfaction index: <strong>%.1f%%</strong></p>
                  <table><thead><tr><th>Stars</th><th style="text-align:right;">Count</th><th style="text-align:right;">%%</th></tr></thead>
                  <tbody>%s</tbody></table>
                  <h2>Net Promoter Score (NPS)</h2>
                  <p>NPS Score: <strong>%d</strong> (from %d respondents)</p>
                  <table style="width:auto;margin-top:8px;"><tr>
                    <td style="background:#d1fae5;color:#065f46;padding:8px 16px;border:none;text-align:center;border-radius:4px;">
                      <div style="font-weight:700;font-size:16px;">%d</div><div>Promoters (9-10)</div>
                    </td>
                    <td style="width:12px;border:none;"></td>
                    <td style="background:#fef3c7;color:#92400e;padding:8px 16px;border:none;text-align:center;border-radius:4px;">
                      <div style="font-weight:700;font-size:16px;">%d</div><div>Passives (7-8)</div>
                    </td>
                    <td style="width:12px;border:none;"></td>
                    <td style="background:#fee2e2;color:#991b1b;padding:8px 16px;border:none;text-align:center;border-radius:4px;">
                      <div style="font-weight:700;font-size:16px;">%d</div><div>Detractors (0-6)</div>
                    </td>
                  </tr></table>
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
                logoHtml, range,
                stats.overallDistribution().average(), stats.overallDistribution().total(),
                stats.satisfactionIndex(),
                distributionRows.toString(),
                nps.score(), nps.respondents(),
                nps.promoters(), nps.passives(), nps.detractors(),
                criteriaRows.isEmpty() ? "" : "<h2>Per-Criterion Averages</h2><table><thead><tr><th>Criterion</th><th style=\"text-align:right;\">Avg Score</th></tr></thead><tbody>" + criteriaRows + "</tbody></table>",
                commentsHtml.isEmpty() ? "" : "<h2>Recent Comments</h2>" + commentsHtml,
                escape(narrative != null ? narrative : ""),
                escape(preparedBy), generated
        );
    }

    private String buildContext(LasemaSatisfactionStats stats, String range) {
        NpsResult nps = stats.nps();
        return String.format(
                "Lasema satisfaction report for %s. Average rating: %.2f out of 5 from %d responses. "
                + "Satisfaction index: %.1f%%. NPS score: %d (promoters: %d, passives: %d, detractors: %d, respondents: %d).",
                range, stats.overallDistribution().average(), stats.overallDistribution().total(),
                stats.satisfactionIndex(), nps.score(),
                nps.promoters(), nps.passives(), nps.detractors(), nps.respondents()
        );
    }

    private String humanizeKey(String key) {
        if (key == null) return "";
        String spaced = key.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
