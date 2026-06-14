package com.sumicare.report.service;

import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.SessionRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class CommissionReportService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final CommissionRepository commissionRepository;
    private final SessionRepository sessionRepository;
    private final TherapistRepository therapistRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public CommissionReportService(CommissionRepository commissionRepository,
                                   SessionRepository sessionRepository,
                                   TherapistRepository therapistRepository,
                                   ShiftRepository shiftRepository,
                                   ShiftAssignmentRepository shiftAssignmentRepository) {
        this.commissionRepository = commissionRepository;
        this.sessionRepository = sessionRepository;
        this.therapistRepository = therapistRepository;
        this.shiftRepository = shiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }

    public record TherapistRow(UUID therapistId, String nickname, BigDecimal total) {}
    public record ShiftReport(Long shiftId, String shiftLabel, LocalDate date, List<TherapistRow> rows, BigDecimal grandTotal) {}
    public record DailyReport(LocalDate date, List<TherapistRow> rows, BigDecimal grandTotal) {}
    public record MatrixRow(UUID therapistId, String nickname, List<BigDecimal> amounts, BigDecimal total) {}
    public record MatrixReport(List<String> columnLabels, List<MatrixRow> rows, List<BigDecimal> columnTotals, BigDecimal grandTotal) {}

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ShiftReport shift(UUID organizationId, Long shiftId, LocalDate date) {
        Shift shift = shiftRepository.findById(shiftId).orElseThrow();
        OffsetDateTime windowStart = date.atTime(shift.getStartTime()).atZone(MANILA).toOffsetDateTime();
        OffsetDateTime windowEnd = shift.getEndTime().isBefore(shift.getStartTime())
                ? date.plusDays(1).atTime(shift.getEndTime()).atZone(MANILA).toOffsetDateTime()
                : date.atTime(shift.getEndTime()).atZone(MANILA).toOffsetDateTime();

        java.util.Set<UUID> assignedTherapistIds = new java.util.HashSet<>();
        for (ShiftAssignment sa : shiftAssignmentRepository.findAllByShiftId(shiftId)) {
            assignedTherapistIds.add(sa.getTherapistId());
        }

        Map<UUID, BigDecimal> totals = totalsByTherapistInWindow(organizationId, windowStart, windowEnd);
        totals.keySet().retainAll(assignedTherapistIds);
        List<TherapistRow> rows = toTherapistRows(organizationId, totals);
        BigDecimal grand = rows.stream().map(TherapistRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShiftReport(shiftId, shift.getLabel(), date, rows, grand);
    }

    public byte[] shiftCsv(UUID organizationId, Long shiftId, LocalDate date) {
        ShiftReport r = shift(organizationId, shiftId, date);
        StringBuilder sb = new StringBuilder();
        sb.append("Therapist,Commission\n");
        for (TherapistRow row : r.rows()) {
            sb.append(csvCell(row.nickname())).append(',').append(row.total().toPlainString()).append('\n');
        }
        sb.append("\nTOTAL,").append(r.grandTotal().toPlainString()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public DailyReport daily(UUID organizationId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);
        Map<UUID, BigDecimal> totals = totalsByTherapistInWindow(organizationId, from, to);
        List<TherapistRow> rows = toTherapistRows(organizationId, totals);
        BigDecimal grand = rows.stream().map(TherapistRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailyReport(date, rows, grand);
    }

    public byte[] dailyCsv(UUID organizationId, LocalDate date) {
        DailyReport r = daily(organizationId, date);
        StringBuilder sb = new StringBuilder();
        sb.append("Therapist,Commission\n");
        for (TherapistRow row : r.rows()) {
            sb.append(csvCell(row.nickname())).append(',').append(row.total().toPlainString()).append('\n');
        }
        sb.append("\nTOTAL,").append(r.grandTotal().toPlainString()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public MatrixReport cutoff(UUID organizationId, int year, int month, int half) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = half == 1 ? ym.atDay(1) : ym.atDay(16);
        LocalDate end = half == 1 ? ym.atDay(15) : ym.atEndOfMonth();
        return matrix(organizationId, start, end);
    }

    public byte[] cutoffCsv(UUID organizationId, int year, int month, int half) {
        return matrixCsv(cutoff(organizationId, year, month, half));
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public MatrixReport monthly(UUID organizationId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return matrix(organizationId, ym.atDay(1), ym.atEndOfMonth());
    }

    public byte[] monthlyCsv(UUID organizationId, int year, int month) {
        return matrixCsv(monthly(organizationId, year, month));
    }

    private MatrixReport matrix(UUID organizationId, LocalDate start, LocalDate end) {
        OffsetDateTime from = start.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = end.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();

        List<Commission> commissions = commissionRepository
                .findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        Map<UUID, Session> sessionsById = new HashMap<>();
        for (Commission c : commissions) {
            sessionsById.computeIfAbsent(c.getSessionId(),
                    id -> sessionRepository.findById(id).orElse(null));
        }

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d-MMM");
        List<LocalDate> days = new ArrayList<>();
        List<String> dayLabels = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            days.add(cur);
            dayLabels.add(cur.format(dayFmt));
            cur = cur.plusDays(1);
        }

        Map<UUID, Map<LocalDate, BigDecimal>> grid = new HashMap<>();
        for (Commission c : commissions) {
            Session s = sessionsById.get(c.getSessionId());
            LocalDate day = (s != null && s.getStartedAt() != null)
                    ? s.getStartedAt().atZoneSameInstant(MANILA).toLocalDate()
                    : c.getCreatedAt().atZoneSameInstant(MANILA).toLocalDate();
            if (day.isBefore(start) || day.isAfter(end)) continue;
            grid.computeIfAbsent(c.getTherapistId(), k -> new HashMap<>())
                .merge(day, c.getAmount(), BigDecimal::add);
        }

        Map<UUID, String> nicknames = new LinkedHashMap<>();
        for (UUID tid : grid.keySet()) {
            therapistRepository.findById(tid).ifPresent(t -> nicknames.put(tid, t.getNickname()));
        }
        List<MatrixRow> rows = new ArrayList<>();
        List<BigDecimal> columnTotals = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) columnTotals.add(BigDecimal.ZERO);
        BigDecimal grand = BigDecimal.ZERO;

        for (Map.Entry<UUID, Map<LocalDate, BigDecimal>> entry : grid.entrySet()) {
            UUID tid = entry.getKey();
            Map<LocalDate, BigDecimal> dayMap = entry.getValue();
            List<BigDecimal> rowAmounts = new ArrayList<>();
            BigDecimal rowTotal = BigDecimal.ZERO;
            for (int i = 0; i < days.size(); i++) {
                BigDecimal v = dayMap.getOrDefault(days.get(i), BigDecimal.ZERO);
                rowAmounts.add(v);
                rowTotal = rowTotal.add(v);
                columnTotals.set(i, columnTotals.get(i).add(v));
            }
            grand = grand.add(rowTotal);
            rows.add(new MatrixRow(tid, nicknames.getOrDefault(tid, tid.toString()), rowAmounts, rowTotal));
        }
        rows.sort((a, b) -> b.total().compareTo(a.total()));

        return new MatrixReport(dayLabels, rows, columnTotals, grand);
    }

    private byte[] matrixCsv(MatrixReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name");
        for (String label : report.columnLabels()) sb.append(',').append(label);
        sb.append(",TOTAL\n");
        for (MatrixRow row : report.rows()) {
            sb.append(csvCell(row.nickname()));
            for (BigDecimal amt : row.amounts()) {
                sb.append(',').append(amt == null ? "0" : amt.toPlainString());
            }
            sb.append(',').append(row.total().toPlainString()).append('\n');
        }
        sb.append("TOTAL");
        for (BigDecimal t : report.columnTotals()) sb.append(',').append(t.toPlainString());
        sb.append(',').append(report.grandTotal().toPlainString()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<UUID, BigDecimal> totalsByTherapistInWindow(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        List<Commission> commissions = commissionRepository
                .findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (Commission c : commissions) {
            totals.merge(c.getTherapistId(), c.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    private List<TherapistRow> toTherapistRows(UUID organizationId, Map<UUID, BigDecimal> totals) {
        List<TherapistRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> e : totals.entrySet()) {
            String nickname = therapistRepository.findById(e.getKey())
                    .map(Therapist::getNickname).orElse(e.getKey().toString());
            rows.add(new TherapistRow(e.getKey(), nickname, e.getValue()));
        }
        rows.sort((a, b) -> b.total().compareTo(a.total()));
        return rows;
    }

    private String csvCell(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
