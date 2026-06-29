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
import com.sumicare.report.dto.TopTherapistResponse;
import com.sumicare.report.dto.TopTherapistResponse.Entry;
import com.sumicare.report.service.TopTherapistService.Period;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class TopTherapistPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final TopTherapistService topTherapistService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;
    private final GeminiNarrativeService geminiNarrativeService;

    public TopTherapistPdfService(TopTherapistService topTherapistService,
                                  OrganizationRepository organizationRepository,
                                  UserRepository userRepository,
                                  PdfRenderer pdfRenderer,
                                  LogoResolver logoResolver,
                                  GeminiNarrativeService geminiNarrativeService) {
        this.topTherapistService = topTherapistService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.pdfRenderer = pdfRenderer;
        this.logoResolver = logoResolver;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] generate(UUID organizationId, UUID preparedByUserId, Period period) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        TopTherapistResponse result = topTherapistService.topTherapists(organizationId, period);
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);

        StringBuilder rows = new StringBuilder();
        int rank = 1;
        for (Entry e : result.therapists()) {
            rows.append("<tr>")
                    .append("<td>").append(rank++).append("</td>")
                    .append("<td>").append(escape(e.nickname())).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(String.format("%.2f", e.averageRating())).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(e.ratingCount()).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(e.requestCount()).append("</td>")
                    .append("<td style=\"text-align:right;\">").append(e.serviceCount()).append("</td>")
                    .append("<td style=\"text-align:right;font-weight:700;\">").append(String.format("%.2f", e.score())).append("</td>")
                    .append("</tr>");
        }

        String logoDataUri = logoResolver.dataUriOrNull(org.getLogoUrl());
        String logoHtml = logoDataUri != null
                ? "<img src=\"" + logoDataUri + "\" style=\"max-height:48px;\" />"
                : "<div style=\"font-size:18px;font-weight:700;color:#c42441;\">" + escape(org.getDisplayName()) + "</div>";

        String context = buildContext(result, period);
        String narrative = geminiNarrativeService.generateInterpretation(context);

        String html = """
                <html>
                <head><style>
                  @page { size: A4 portrait; margin: 18mm 14mm; }
                  body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1a1a1a; font-size: 11px; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 12px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 5px 8px; }
                  th { background: #f8fafc; text-align: left; font-weight: 700; }
                  .note { font-size: 9px; color: #6b7280; margin-top: 4px; }
                  .narrative { background: #f8fafc; border-left: 3px solid #1e406e; padding: 10px 14px; margin-top: 12px; line-height: 1.6; }
                  .footer { margin-top: 28px; font-size: 9px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <div style="display:flex;justify-content:space-between;align-items:center;">
                    %s
                    <div style="text-align:right;font-size:11px;color:#6b7280;">
                      <div style="font-size:15px;font-weight:700;color:#1a1a1a;">Top 10 Therapists Report</div>
                      <div>Period: %s</div>
                    </div>
                  </div>
                  <p class="note">Composite score = 50%% average client rating + 25%% specific request rate + 25%% services rendered rate.</p>
                  <table>
                    <thead><tr>
                      <th>#</th><th>Therapist</th>
                      <th style="text-align:right;">Avg Rating</th>
                      <th style="text-align:right;">Ratings</th>
                      <th style="text-align:right;">Requests</th>
                      <th style="text-align:right;">Services</th>
                      <th style="text-align:right;">Score</th>
                    </tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <div style="margin-top:14px;font-size:11px;font-weight:700;color:#1e406e;">Financial Interpretation</div>
                  <div class="narrative">%s</div>
                  <div style="margin-top:18px;font-size:10px;color:#374151;">
                    <div>Prepared By: %s</div>
                    <div>Generated: %s (Manila)</div>
                  </div>
                  <div class="footer">Powered by SumiCare</div>
                </body>
                </html>
                """.formatted(logoHtml, periodLabel(period), rows.toString(),
                escape(narrative != null ? narrative : ""), escape(preparedBy), generated);

        return pdfRenderer.renderHtml(html);
    }

    private String buildContext(TopTherapistResponse result, Period period) {
        StringBuilder sb = new StringBuilder();
        sb.append("Top 10 Therapists ranking for period: ").append(periodLabel(period)).append(". ");
        int rank = 1;
        for (Entry e : result.therapists()) {
            sb.append("Rank ").append(rank++).append(": ").append(e.nickname())
                    .append(", avg rating ").append(String.format("%.2f", e.averageRating()))
                    .append(", requests ").append(e.requestCount())
                    .append(", services ").append(e.serviceCount())
                    .append(", score ").append(String.format("%.2f", e.score())).append(". ");
        }
        return sb.toString();
    }

    private String periodLabel(Period period) {
        return switch (period) {
            case WEEKLY -> "This Week";
            case MONTHLY -> "This Month";
            case PAYROLL -> "Current Payroll Period";
            case ALL -> "All Time";
        };
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
