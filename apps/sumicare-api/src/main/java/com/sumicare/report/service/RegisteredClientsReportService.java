/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.client.domain.Client;
import com.sumicare.client.dto.ClientUsageResponse;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.client.service.ClientUsageService;
import com.sumicare.common.util.LogoResolver;
import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.print.PdfRenderer;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RegisteredClientsReportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final ClientRepository clientRepository;
    private final ClientUsageService clientUsageService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PdfRenderer pdfRenderer;
    private final LogoResolver logoResolver;

    public RegisteredClientsReportService(ClientRepository clientRepository,
                                          ClientUsageService clientUsageService,
                                          OrganizationRepository organizationRepository,
                                          UserRepository userRepository,
                                          PdfRenderer pdfRenderer,
                                          LogoResolver logoResolver) {
        this.clientRepository = clientRepository;
        this.clientUsageService = clientUsageService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.pdfRenderer = pdfRenderer;
        this.logoResolver = logoResolver;
    }

    public record ClientRow(UUID clientId, String nickname, int bookingCount, BigDecimal totalSpending,
                            String topService, String topPackage) {}
    public record RegisteredClientsReport(List<ClientRow> clients, BigDecimal totalLifetimeSpend) {}

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public RegisteredClientsReport report(UUID organizationId) {
        List<ClientRow> rows = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Client client : clientRepository.findAllByOrganizationIdAndDeletedAtIsNullOrderByNicknameAsc(organizationId)) {
            ClientUsageResponse usage = clientUsageService.forClient(organizationId, client.getId());
            BigDecimal spend = usage.totalSpending() == null ? BigDecimal.ZERO : usage.totalSpending();
            rows.add(new ClientRow(client.getId(), usage.nickname(), usage.bookingCount(), spend,
                    firstLabel(usage.topServices()), firstLabel(usage.topPackages())));
            grand = grand.add(spend);
        }
        rows.sort(Comparator.comparing(ClientRow::totalSpending).reversed());
        return new RegisteredClientsReport(rows, grand);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] pdf(UUID organizationId, UUID preparedByUserId) {
        RegisteredClientsReport report = report(organizationId);
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");
        String logo = logoResolver.dataUriOrNull(org.getLogoUrl());
        String header = logo != null
                ? "<img src=\"" + logo + "\" style=\"max-height: 48px;\" />"
                : "<div style=\"font-size: 20px; font-weight: 700; color: #c42441;\">" + escape(org.getDisplayName()) + "</div>";
        String generated = OffsetDateTime.now().atZoneSameInstant(MANILA).format(STAMP);

        StringBuilder rows = new StringBuilder();
        int rank = 1;
        for (ClientRow c : report.clients()) {
            rows.append("<tr>")
                .append(cell(String.valueOf(rank++), "right"))
                .append(cell(escape(c.nickname()), "left"))
                .append(cell(String.valueOf(c.bookingCount()), "right"))
                .append(cell(peso(c.totalSpending()), "right"))
                .append(cell(escape(c.topService()), "left"))
                .append(cell(escape(c.topPackage()), "left"))
                .append("</tr>");
        }

        String html = """
                <html>
                <head><style>
                  @page { size: A4 portrait; margin: 18mm 14mm; }
                  body { font-family: 'DejaVu Sans', 'Liberation Sans', Arial, Helvetica, sans-serif; color: #1a1a1a; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 12px; font-size: 11px; }
                  th, td { border-bottom: 1px solid #e5e7eb; padding: 6px 8px; }
                  th { background: #f8fafc; text-align: left; }
                  .footer { margin-top: 28px; font-size: 10px; color: #9ca3af; text-align: center; }
                </style></head>
                <body>
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    %s
                    <div style="text-align: right; font-size: 11px; color: #6b7280;">
                      <div style="font-size: 15px; font-weight: 700; color: #1a1a1a;">Registered Clients Report</div>
                      <div>%s clients</div>
                    </div>
                  </div>
                  <table>
                    <thead><tr><th style="text-align:right;">#</th><th>Client</th><th style="text-align:right;">Bookings</th>
                      <th style="text-align:right;">Lifetime spend</th><th>Favourite service</th><th>Favourite package</th></tr></thead>
                    <tbody>%s</tbody>
                    <tfoot><tr><th></th><th>Total lifetime spend</th><th></th><th style="text-align:right;">%s</th><th></th><th></th></tr></tfoot>
                  </table>
                  <div style="margin-top: 18px; font-size: 11px; color: #374151;">
                    <div>Prepared By: %s</div>
                    <div>Generated: %s (Manila)</div>
                  </div>
                  <div class="footer">Powered by SumiCare</div>
                </body>
                </html>
                """.formatted(header, report.clients().size(), rows.toString(),
                        peso(report.totalLifetimeSpend()), escape(preparedBy), generated);

        return pdfRenderer.renderHtml(html);
    }

    private String firstLabel(List<ClientUsageResponse.UsageCount> counts) {
        return counts == null || counts.isEmpty() ? "" : counts.get(0).label();
    }

    private String cell(String value, String align) {
        return "<td style=\"text-align:" + align + ";\">" + value + "</td>";
    }

    private String peso(BigDecimal value) {
        return "₱ " + (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
