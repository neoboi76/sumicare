/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.report.service.TherapistPerformanceService.TherapistPerformance;
import com.sumicare.report.service.TherapistPerformanceService.TherapistPerformanceReport;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class PerformanceMonitoringExcelService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final TherapistPerformanceService therapistPerformanceService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ExcelExportService excelExportService;
    private final GeminiNarrativeService geminiNarrativeService;

    public PerformanceMonitoringExcelService(TherapistPerformanceService therapistPerformanceService,
                                             OrganizationRepository organizationRepository,
                                             UserRepository userRepository,
                                             ExcelExportService excelExportService,
                                             GeminiNarrativeService geminiNarrativeService) {
        this.therapistPerformanceService = therapistPerformanceService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.excelExportService = excelExportService;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] generate(UUID organizationId, UUID preparedByUserId, LocalDate from, LocalDate to) {
        TherapistPerformanceReport report = therapistPerformanceService.report(organizationId, from, to);
        String orgLogoUrl = organizationRepository.findById(organizationId)
                .map(o -> o.getLogoUrl()).orElse(null);
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");
        String range = from + " to " + to;

        ExcelExportService.WorkbookContext ctx = excelExportService.createWorkbook(
                "Performance Monitoring",
                "Performance Monitoring Report",
                "Period: " + range,
                preparedBy,
                orgLogoUrl
        );

        excelExportService.writeHeaderRow(ctx, List.of(
                "#", "Therapist", "Revenue", "Commissions", "Tips",
                "Services Rendered", "Specific Requests", "Avg Rating", "Satisfaction Index (%)"
        ));

        int idx = 1;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        long totalServices = 0;
        long totalRequests = 0;

        for (TherapistPerformance p : report.therapists()) {
            excelExportService.writeDataRow(ctx, List.of(
                    idx++,
                    p.nickname(),
                    p.revenue(),
                    p.commissions(),
                    p.tips(),
                    p.servicesRendered(),
                    p.specificRequests(),
                    p.averageSatisfactionRating(),
                    p.satisfactionIndex()
            ));
            totalRevenue = totalRevenue.add(p.revenue());
            totalCommissions = totalCommissions.add(p.commissions());
            totalTips = totalTips.add(p.tips());
            totalServices += p.servicesRendered();
            totalRequests += p.specificRequests();
        }

        excelExportService.writeTotalRow(ctx, List.of(
                "", "Totals",
                totalRevenue, totalCommissions, totalTips,
                totalServices, totalRequests, "", ""
        ));

        excelExportService.writeBlankRow(ctx);

        String context = buildNarrativeContext(report, from, to);
        String narrative = geminiNarrativeService.generateInterpretation(context);
        excelExportService.writeNarrativeSection(ctx, "Financial Interpretation", narrative, 9);
        excelExportService.writeFooter(ctx, 9);
        excelExportService.autoSizeColumns(ctx, 9);

        return excelExportService.toBytes(ctx.workbook);
    }

    private String buildNarrativeContext(TherapistPerformanceReport report, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        sb.append("Performance monitoring report from ").append(from).append(" to ").append(to).append(". ");
        sb.append(report.therapists().size()).append(" therapist(s) active. ");
        for (TherapistPerformance p : report.therapists()) {
            sb.append(p.nickname())
                    .append(": revenue P").append(String.format("%,.2f", p.revenue()))
                    .append(", commissions P").append(String.format("%,.2f", p.commissions()))
                    .append(", tips P").append(String.format("%,.2f", p.tips()))
                    .append(", services ").append(p.servicesRendered())
                    .append(", requests ").append(p.specificRequests())
                    .append(", avg rating ").append(String.format("%.2f", p.averageSatisfactionRating()))
                    .append(". ");
        }
        return sb.toString();
    }
}
