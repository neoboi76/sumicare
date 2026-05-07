package com.sumicare.report.service;

import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.transaction.repository.CommissionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final SessionRepository sessionRepository;
    private final CommissionRepository commissionRepository;

    public ReportService(SessionRepository sessionRepository, CommissionRepository commissionRepository) {
        this.sessionRepository = sessionRepository;
        this.commissionRepository = commissionRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ReportSummary buildCutoffReport(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        var sessions = sessionRepository.findAllByOrganizationIdAndStartedAtBetween(organizationId, from, to);
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
    public byte[] exportCutoffToCsv(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        ReportSummary summary = buildCutoffReport(organizationId, from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("Therapist ID,Sessions,Specifically Requested,Total Commission\n");
        for (var entry : summary.sessionCountByTherapist().entrySet()) {
            UUID therapistId = entry.getKey();
            int sessions = entry.getValue();
            int requested = summary.requestedCountByTherapist().getOrDefault(therapistId, 0);
            BigDecimal commission = summary.commissionsByTherapist().getOrDefault(therapistId, BigDecimal.ZERO);
            sb.append(therapistId).append(',')
              .append(sessions).append(',')
              .append(requested).append(',')
              .append(commission.toPlainString()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
