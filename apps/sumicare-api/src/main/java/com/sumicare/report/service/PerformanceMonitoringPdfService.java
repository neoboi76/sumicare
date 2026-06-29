/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.common.util.LogoResolver;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.print.PdfRenderer;
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
import java.util.UUID;

@Service
public class PerformanceMonitoringPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TherapistPerformanceService therapistPerformanceService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;
    private final GeminiNarrativeService geminiNarrativeService;

    public PerformanceMonitoringPdfService(TherapistPerformanceService therapistPerformanceService,
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
    public byte[] generate(UUID organizationId, UUID preparedByUserId, LocalDate from, LocalDate to) {
        TherapistPerformanceReport report = therapistPerformanceService.report(organizationId, from, to);
        String orgLogoUrl = organizationRepository.findById(organizationId)
                .map(o -> o.getLogoUrl()).orElse(null);
        String orgDisplayName = organizationRepository.findById(organizationId)
                .map(o -> o.getDisplayName()).orElse("SumiCare");
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");
        String logoDataUri = logoResolver.dataUriOrNull(orgLogoUrl);
        String logoHtml = logoDataUri != null
                ? "<img src=\"" + logoDataUri + "\" style=\"max-height:48px;\" />"
                : "<div style=\"font-size:18px;font-weight:700;color:#c42441;\">" + escape(orgDisplayName) + "</div>";
        String range = DAY.format(from) + " \u2013 " + DAY.format(to);
        String generated = STAMP.format(OffsetDateTime.now(MANILA));

        StringBuilder rows = new StringBuilder();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        long totalServices = 0;
        long totalRequests = 0;

        int idx = 1;
        for (TherapistPerformance p : report.therapists()) {
            String rowStyle = idx % 2 == 0 ? "background:#f8fafc;" : "";
            rows.append(String.format(
                    "<tr style=\"%s\"><td>%d</td><td>%s</td><td style=\"text-align:right;\">%s</td>"
                    + "<td style=\"text-align:right;\">%s</td><td style=\"text-align:right;\">%s</td>"
                    + "<td style=\"text-align:right;\">%d</td><td style=\"text-align:right;\">%d</td>"
                    + "<td style=\"text-align:right;\">%.2f / 5</td><td style=\"text-align:right;\">%.1f%%</td></tr>",
                    rowStyle, idx++, escape(p.nickname()),
                    peso(p.revenue()), peso(p.commissions()), peso(p.tips()),
                    p.servicesRendered(), p.specificRequests(),
                    p.averageSatisfactionRating(), p.satisfactionIndex()
            ));
            totalRevenue = totalRevenue.add(p.revenue());
            totalCommissions = totalCommissions.add(p.commissions());
            totalTips = totalTips.add(p.tips());
            totalServices += p.servicesRendered();
            totalRequests += p.specificRequests();
        }

        rows.append(String.format(
                "<tr style=\"font-weight:700;background:#f1f5f9;\"><td colspan=\"2\">Totals</td>"
                + "<td style=\"text-align:right;\">%s</td><td style=\"text-align:right;\">%s</td>"
                + "<td style=\"text-align:right;\">%s</td><td style=\"text-align:right;\">%d</td>"
                + "<td style=\"text-align:right;\">%d</td><td></td><td></td></tr>",
                peso(totalRevenue), peso(totalCommissions), peso(totalTips), totalServices, totalRequests
        ));

        String context = buildNarrativeContext(report, from, to);
        String narrative = geminiNarrativeService.generateInterpretation(context);

        String html = String.format("""
                <html>
                <head><style>
                  @page { size: A4 landscape; margin: 14mm 12mm; }
                  body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1a1a1a; font-size: 10px; }
                  h2 { font-size: 12px; color: #1e406e; margin: 14px 0 5px 0; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 4px 6px; }
                  th { background: #f8fafc; text-align: left; font-weight: 700; font-size: 9px; text-transform: uppercase; }
                  .narrative { background: #f8fafc; border-left: 3px solid #1e406e; padding: 8px 12px; margin-top: 10px; line-height: 1.6; }
                  .footer { margin-top: 22px; font-size: 9px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <table style="width:100%%;border:none;margin:0 0 8px 0;"><tr>
                    <td style="border:none;padding:0;">%s</td>
                    <td style="border:none;padding:0;text-align:right;font-size:10px;color:#6b7280;">
                      <div style="font-size:14px;font-weight:700;color:#1a1a1a;">Performance Monitoring Report</div>
                      <div>%s</div>
                    </td>
                  </tr></table>
                  <h2>Therapist Performance Summary</h2>
                  <table>
                    <thead><tr>
                      <th>#</th><th>Therapist</th><th style="text-align:right;">Revenue</th>
                      <th style="text-align:right;">Commissions</th><th style="text-align:right;">Tips</th>
                      <th style="text-align:right;">Services</th><th style="text-align:right;">Requests</th>
                      <th style="text-align:right;">Avg Rating</th><th style="text-align:right;">Satisfaction</th>
                    </tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <h2>Financial Interpretation</h2>
                  <div class="narrative">%s</div>
                  <div style="margin-top:14px;font-size:9px;color:#374151;">
                    <div>Prepared By: %s</div>
                    <div>Generated: %s (Manila)</div>
                  </div>
                  <div class="footer">Powered by SumiCare</div>
                </body>
                </html>
                """, logoHtml, range, rows.toString(), narrative, preparedBy, generated);

        return pdfRenderer.renderHtml(html);
    }

    private String buildNarrativeContext(TherapistPerformanceReport report, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        sb.append("Performance monitoring report from ").append(from).append(" to ").append(to).append(". ");
        sb.append(report.therapists().size()).append(" therapist(s) active. ");
        for (TherapistPerformance p : report.therapists()) {
            sb.append(p.nickname())
                    .append(": revenue ").append(peso(p.revenue()))
                    .append(", commissions ").append(peso(p.commissions()))
                    .append(", tips ").append(peso(p.tips()))
                    .append(", services ").append(p.servicesRendered())
                    .append(", requests ").append(p.specificRequests())
                    .append(", avg rating ").append(String.format("%.2f", p.averageSatisfactionRating()))
                    .append(". ");
        }
        return sb.toString();
    }

    private String peso(BigDecimal v) {
        return "P" + String.format("%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
