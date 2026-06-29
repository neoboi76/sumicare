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
import com.sumicare.report.service.OperationsReportService.CutoffServicesReport;
import com.sumicare.report.service.OperationsReportService.ServiceLine;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ReportPdfService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final OperationsReportService operationsReportService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;
    private final GeminiNarrativeService geminiNarrativeService;

    public ReportPdfService(OperationsReportService operationsReportService,
                            OrganizationRepository organizationRepository,
                            UserRepository userRepository,
                            PdfRenderer pdfRenderer,
                            LogoResolver logoResolver,
                            GeminiNarrativeService geminiNarrativeService) {
        this.operationsReportService = operationsReportService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.pdfRenderer = pdfRenderer;
        this.logoResolver = logoResolver;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] salesByService(UUID organizationId, UUID preparedByUserId,
                                  OffsetDateTime from, OffsetDateTime to) {
        return salesByService(organizationId, preparedByUserId, from, to, SalesGroupBy.SERVICE);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] salesByService(UUID organizationId, UUID preparedByUserId,
                                  OffsetDateTime from, OffsetDateTime to, SalesGroupBy groupBy) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        CutoffServicesReport report = operationsReportService.cutoffServices(organizationId, from, to, null, groupBy);

        StringBuilder rows = new StringBuilder();
        for (ServiceLine line : report.lines()) {
            rows.append("<tr>")
                .append(cell(escape(line.serviceName()), "left"))
                .append(cell(String.valueOf(line.qty()), "right"))
                .append(cell(peso(line.unitPrice()), "right"))
                .append(cell(peso(line.lineTotal()), "right"))
                .append("</tr>");
        }

        String logoDataUri = logoResolver.dataUriOrNull(org.getLogoUrl());
        String header = logoDataUri != null
                ? "<img src=\"" + logoDataUri + "\" style=\"max-height: 48px;\" />"
                : "<div style=\"font-size: 20px; font-weight: 700; color: #c42441;\">" + escape(org.getDisplayName()) + "</div>";

        String range = DAY.format(from.atZoneSameInstant(MANILA)) + " to " + DAY.format(to.minusSeconds(1).atZoneSameInstant(MANILA));
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);

        String reportContext = buildContext(report, groupBy, range);
        String narrative = geminiNarrativeService.generateInterpretation(reportContext);

        String html = """
                <html>
                <head><style>
                  @page { size: A4 portrait; margin: 18mm 14mm; }
                  body { font-family: 'DejaVu Sans', 'Liberation Sans', Arial, Helvetica, sans-serif; color: #1a1a1a; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 12px; font-size: 11px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 6px 8px; }
                  th { background: #f8fafc; text-align: left; }
                  .narrative { background: #f8fafc; border-left: 3px solid #1e406e; padding: 10px 14px; margin-top: 14px; font-size: 11px; line-height: 1.6; }
                  .section-title { font-size: 13px; font-weight: 700; color: #1e406e; margin: 18px 0 4px 0; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; }
                  .footer { margin-top: 28px; font-size: 10px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    %s
                    <div style="text-align: right; font-size: 11px; color: #6b7280;">
                      <div style="font-size: 15px; font-weight: 700; color: #1a1a1a;">Sales Report by %s</div>
                      <div>%s</div>
                    </div>
                  </div>
                  <table>
                    <thead><tr><th>%s</th><th style="text-align:right;">Qty</th><th style="text-align:right;">Unit price</th><th style="text-align:right;">Total</th></tr></thead>
                    <tbody>%s</tbody>
                    <tfoot><tr><th>Grand total</th><th></th><th></th><th style="text-align:right;">%s</th></tr></tfoot>
                  </table>
                  <div class="section-title">Financial Interpretation</div>
                  <div class="narrative">%s</div>
                  <div style="margin-top: 18px; font-size: 11px; color: #374151;">
                    <div>Prepared By: %s</div>
                    <div>Generated: %s (Manila)</div>
                  </div>
                  <div class="footer">Powered by SumiCare</div>
                </body>
                </html>
                """.formatted(
                header,
                escape(groupBy.displayLabel()),
                range,
                escape(groupBy.displayLabel()),
                rows.toString(),
                peso(report.grandTotal()),
                escape(narrative != null ? narrative : ""),
                escape(preparedBy),
                generated
        );

        return pdfRenderer.renderHtml(html);
    }

    private String buildContext(CutoffServicesReport report, SalesGroupBy groupBy, String range) {
        return String.format(
                "Sales report grouped by %s for %s. Grand total: %s. Number of line items: %d.",
                groupBy.displayLabel(), range,
                report.grandTotal().toPlainString(), report.lines().size()
        );
    }

    private String cell(String value, String align) {
        return "<td style=\"text-align:" + align + ";\">" + value + "</td>";
    }

    private String peso(BigDecimal value) {
        return "&#8369; " + (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
