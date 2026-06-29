/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.report.dto.TopTherapistResponse;
import com.sumicare.report.dto.TopTherapistResponse.Entry;
import com.sumicare.report.service.TopTherapistService.Period;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class TopTherapistExcelService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final TopTherapistService topTherapistService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ExcelExportService excelExportService;
    private final GeminiNarrativeService geminiNarrativeService;

    public TopTherapistExcelService(TopTherapistService topTherapistService,
                                    OrganizationRepository organizationRepository,
                                    UserRepository userRepository,
                                    ExcelExportService excelExportService,
                                    GeminiNarrativeService geminiNarrativeService) {
        this.topTherapistService = topTherapistService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.excelExportService = excelExportService;
        this.geminiNarrativeService = geminiNarrativeService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public byte[] generate(UUID organizationId, UUID preparedByUserId, Period period) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        String preparedBy = userRepository.findById(preparedByUserId)
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        TopTherapistResponse result = topTherapistService.topTherapists(organizationId, period);
        String today = LocalDate.now(MANILA).toString();

        ExcelExportService.WorkbookContext ctx = excelExportService.createWorkbook(
                "Top 10 Therapists",
                "Top 10 Therapists Report — " + periodLabel(period),
                "Period: " + periodLabel(period) + " (as of " + today + ")",
                preparedBy,
                org.getLogoUrl()
        );

        excelExportService.writeHeaderRow(ctx, List.of(
                "Rank", "Therapist", "Avg Rating", "Rating Count",
                "Specific Requests", "Services Rendered", "Composite Score"
        ));

        int rank = 1;
        for (Entry e : result.therapists()) {
            excelExportService.writeDataRow(ctx, List.of(
                    rank++,
                    e.nickname(),
                    e.averageRating(),
                    e.ratingCount(),
                    e.requestCount(),
                    e.serviceCount(),
                    e.score()
            ));
        }

        excelExportService.writeBlankRow(ctx);

        String context = buildContext(result, period);
        String narrative = geminiNarrativeService.generateInterpretation(context);
        excelExportService.writeNarrativeSection(ctx, "Financial Interpretation", narrative, 7);
        excelExportService.writeFooter(ctx, 7);
        excelExportService.autoSizeColumns(ctx, 7);

        return excelExportService.toBytes(ctx.workbook);
    }

    private String buildContext(TopTherapistResponse result, Period period) {
        StringBuilder sb = new StringBuilder();
        sb.append("Top 10 Therapists ranking for period: ").append(periodLabel(period)).append(". ");
        int rank = 1;
        for (Entry e : result.therapists()) {
            sb.append("Rank ").append(rank++).append(": ").append(e.nickname())
                    .append(" (avg rating ").append(String.format("%.2f", e.averageRating()))
                    .append(", requests ").append(e.requestCount())
                    .append(", services ").append(e.serviceCount())
                    .append(", score ").append(String.format("%.2f", e.score())).append("). ");
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
}
