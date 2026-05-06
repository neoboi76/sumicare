package com.sumicare.report.service;

import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.transaction.repository.CommissionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
    public byte[] exportCutoffToExcel(UUID organizationId, OffsetDateTime from, OffsetDateTime to) throws IOException {
        ReportSummary summary = buildCutoffReport(organizationId, from, to);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Cutoff");
            int row = 0;
            Row header = sheet.createRow(row++);
            header.createCell(0).setCellValue("Therapist ID");
            header.createCell(1).setCellValue("Sessions");
            header.createCell(2).setCellValue("Specifically Requested");
            header.createCell(3).setCellValue("Total Commission");
            for (var entry : summary.sessionCountByTherapist().entrySet()) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(entry.getKey().toString());
                r.createCell(1).setCellValue(entry.getValue());
                r.createCell(2).setCellValue(summary.requestedCountByTherapist().getOrDefault(entry.getKey(), 0));
                BigDecimal commission = summary.commissionsByTherapist().getOrDefault(entry.getKey(), BigDecimal.ZERO);
                r.createCell(3).setCellValue(commission.doubleValue());
            }
            workbook.write(out);
            return out.toByteArray();
        }
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
