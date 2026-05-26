package com.sumicare.report.service;

import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.domain.ShiftAssignment;
import com.sumicare.shift.repository.ShiftAssignmentRepository;
import com.sumicare.shift.repository.ShiftRepository;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.Commission;
import com.sumicare.transaction.repository.CommissionRepository;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class DeckingReportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final CommissionRepository commissionRepository;
    private final SessionRepository sessionRepository;
    private final TherapistRepository therapistRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ServiceRepository serviceRepository;

    public DeckingReportService(CommissionRepository commissionRepository,
                                SessionRepository sessionRepository,
                                TherapistRepository therapistRepository,
                                ShiftRepository shiftRepository,
                                ShiftAssignmentRepository shiftAssignmentRepository,
                                ServiceRepository serviceRepository) {
        this.commissionRepository = commissionRepository;
        this.sessionRepository = sessionRepository;
        this.therapistRepository = therapistRepository;
        this.shiftRepository = shiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.serviceRepository = serviceRepository;
    }

    public record DeckingGlyph(String symbol, String serviceType) {}
    public record TherapistDeckingRow(UUID therapistId, String nickname, Long shiftId, String shiftLabel,
                                       List<DeckingGlyph> glyphs, BigDecimal totalCommission, int requestedCount) {}
    public record ShiftGroup(Long shiftId, String shiftLabel, List<TherapistDeckingRow> rows) {}
    public record DeckingDailyReport(LocalDate date, List<ShiftGroup> shiftGroups) {}

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public DeckingDailyReport daily(UUID organizationId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);

        List<Commission> commissions = commissionRepository
                .findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        Map<Long, Boolean> scrubCache = new HashMap<>();

        List<Shift> shifts = shiftRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
        Map<UUID, Long> therapistToShift = new HashMap<>();
        for (Shift shift : shifts) {
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findAllByShiftId(shift.getId());
            for (ShiftAssignment sa : assignments) {
                therapistToShift.put(sa.getTherapistId(), shift.getId());
            }
        }

        Map<Long, Shift> shiftMap = new LinkedHashMap<>();
        for (Shift s : shifts) shiftMap.put(s.getId(), s);

        Map<UUID, List<Commission>> byTherapist = new LinkedHashMap<>();
        for (Commission c : commissions) {
            byTherapist.computeIfAbsent(c.getTherapistId(), k -> new ArrayList<>()).add(c);
        }

        Map<Long, List<TherapistDeckingRow>> groupedRows = new LinkedHashMap<>();
        for (Shift s : shifts) groupedRows.put(s.getId(), new ArrayList<>());

        for (Map.Entry<UUID, List<Commission>> entry : byTherapist.entrySet()) {
            UUID therapistId = entry.getKey();
            List<Commission> tComms = entry.getValue();
            Therapist t = therapistRepository.findById(therapistId).orElse(null);
            String nickname = t == null ? therapistId.toString() : t.getNickname();
            Long assignedShiftId = therapistToShift.get(therapistId);

            List<DeckingGlyph> glyphs = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            int requested = 0;

            for (Commission c : tComms) {
                if (c.isExtension()) continue;
                String symbol;
                if (c.isSpecificallyRequested()) {
                    symbol = "\u2665";
                    requested++;
                } else if (isScrub(c.getServiceId(), scrubCache)) {
                    symbol = "\u2605";
                } else {
                    symbol = "|";
                }
                glyphs.add(new DeckingGlyph(symbol, c.getServiceType()));
                total = total.add(c.getAmount());
            }

            for (Commission c : tComms) {
                if (c.isExtension()) {
                    total = total.add(c.getAmount());
                }
            }

            TherapistDeckingRow row = new TherapistDeckingRow(therapistId, nickname,
                    assignedShiftId, assignedShiftId != null && shiftMap.containsKey(assignedShiftId)
                    ? shiftMap.get(assignedShiftId).getLabel() : null,
                    glyphs, total, requested);

            if (assignedShiftId != null && groupedRows.containsKey(assignedShiftId)) {
                groupedRows.get(assignedShiftId).add(row);
            } else if (!shifts.isEmpty()) {
                groupedRows.get(shifts.get(0).getId()).add(row);
            }
        }

        List<ShiftGroup> shiftGroups = new ArrayList<>();
        for (Shift s : shifts) {
            List<TherapistDeckingRow> rows = groupedRows.getOrDefault(s.getId(), List.of());
            shiftGroups.add(new ShiftGroup(s.getId(), s.getLabel(), rows));
        }

        return new DeckingDailyReport(date, shiftGroups);
    }

    public byte[] dailyCsv(UUID organizationId, LocalDate date) {
        DeckingDailyReport report = daily(organizationId, date);
        StringBuilder sb = new StringBuilder();
        sb.append("Shift,Name,Tick Tack,Total Commission,Requested Count\n");
        for (ShiftGroup group : report.shiftGroups()) {
            for (TherapistDeckingRow row : group.rows()) {
                sb.append(csvCell(group.shiftLabel())).append(',');
                sb.append(csvCell(row.nickname())).append(',');
                StringBuilder glyphStr = new StringBuilder();
                for (DeckingGlyph g : row.glyphs()) glyphStr.append(g.symbol());
                sb.append(csvCell(glyphStr.toString())).append(',');
                sb.append(row.totalCommission().toPlainString()).append(',');
                sb.append(row.requestedCount()).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private boolean isScrub(Long serviceId, Map<Long, Boolean> cache) {
        if (serviceId == null) return false;
        return cache.computeIfAbsent(serviceId, id -> serviceRepository.findById(id)
                .map(svc -> isScrubService(svc.getName()))
                .orElse(false));
    }

    private boolean isScrubService(String serviceName) {
        if (serviceName == null) return false;
        String lower = serviceName.toLowerCase();
        return lower.contains("scrub") || lower.contains("salt") || lower.contains("milk bath") || lower.contains("dae mi di");
    }

    private String csvCell(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
