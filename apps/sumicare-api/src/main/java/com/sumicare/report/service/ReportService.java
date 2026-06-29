/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.transaction.repository.CommissionRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final SessionRepository sessionRepository;
    private final CommissionRepository commissionRepository;
    private final ExcelExportService excelExportService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public ReportService(SessionRepository sessionRepository,
                         CommissionRepository commissionRepository,
                         ExcelExportService excelExportService,
                         OrganizationRepository organizationRepository,
                         UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.commissionRepository = commissionRepository;
        this.excelExportService = excelExportService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ReportSummary buildCutoffReport(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        // Rolls commissions and sessions in the window up per therapist; cancelled
        // sessions are excluded so they do not inflate session or requested counts.
        var sessions = sessionRepository.findAllByOrganizationIdAndStartedAtBetween(organizationId, from, to)
                .stream().filter(s -> !"CANCELLED".equals(s.getStatus())).toList();
        var commissions = commissionRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        Map<UUID, BigDecimal> commissionsByTherapist = new HashMap<>();
        Map<UUID, Integer> sessionCountByTherapist = new HashMap<>();
        Map<UUID, Integer> requestedCountByTherapist = new HashMap<>();
        for (var c : commissions) {
            commissionsByTherapist.merge(c.getTherapistId(), c.getAmount(), BigDecimal::add);
        }
        for (var s : sessions) {
            if (s.getPrimaryTherapistId() != null) {
                sessionCountByTherapist.merge(s.getPrimaryTherapistId(), 1, Integer::sum);
                if (s.isSpecificallyRequested()) {
                    requestedCountByTherapist.merge(s.getPrimaryTherapistId(), 1, Integer::sum);
                }
            }
        }
        return new ReportSummary(from, to, sessions.size(), commissions.size(),
                commissionsByTherapist, sessionCountByTherapist, requestedCountByTherapist);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public byte[] exportCutoffToXlsx(UUID organizationId, UUID preparedByUserId,
                                      OffsetDateTime from, OffsetDateTime to) {
        ReportSummary summary = buildCutoffReport(organizationId, from, to);
        String preparedBy = preparedByUserId == null ? "Staff"
                : userRepository.findById(preparedByUserId)
                    .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                    .orElse("Staff");
        String logoUrl = organizationRepository.findById(organizationId).map(o -> o.getLogoUrl()).orElse(null);
        ExcelExportService.WorkbookContext ctx = excelExportService.createWorkbook(
                "Cutoff Report",
                "Cutoff Report",
                from.toLocalDate() + " to " + to.toLocalDate(),
                preparedBy, logoUrl);
        excelExportService.writeHeaderRow(ctx, List.of(
                "Therapist ID", "Sessions", "Specifically Requested", "Total Commission"));
        for (Map.Entry<UUID, Integer> entry : summary.sessionCountByTherapist().entrySet()) {
            UUID therapistId = entry.getKey();
            int sessions = entry.getValue();
            int requested = summary.requestedCountByTherapist().getOrDefault(therapistId, 0);
            BigDecimal commission = summary.commissionsByTherapist().getOrDefault(therapistId, BigDecimal.ZERO);
            excelExportService.writeDataRow(ctx, List.of(
                    therapistId.toString(), sessions, requested, commission));
        }
        excelExportService.writeFooter(ctx, 4);
        excelExportService.autoSizeColumns(ctx, 4);
        return excelExportService.toBytes(ctx.workbook);
    }

    public record ReportSummary(
            OffsetDateTime from,
            OffsetDateTime to,
            int sessionCount,
            int commissionCount,
            Map<UUID, BigDecimal> commissionsByTherapist,
            Map<UUID, Integer> sessionCountByTherapist,
            Map<UUID, Integer> requestedCountByTherapist
    ) {}
}
