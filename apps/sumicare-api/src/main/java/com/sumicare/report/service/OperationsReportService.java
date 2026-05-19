package com.sumicare.report.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.OrderItemAttendee;
import com.sumicare.cashier.repository.OrderItemAttendeeRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.shift.domain.ShiftAssignment;
import com.sumicare.shift.repository.ShiftAssignmentRepository;
import com.sumicare.shift.repository.ShiftRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class OperationsReportService {

    private final SessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final TreatmentSlipRepository slipRepository;
    private final ServiceRepository serviceRepository;
    private final TherapistRepository therapistRepository;
    private final RoomRepository roomRepository;
    private final OrderRepository orderRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final OrderItemAttendeeRepository attendeeRepository;

    public OperationsReportService(SessionRepository sessionRepository,
                                   BookingRepository bookingRepository,
                                   TreatmentSlipRepository slipRepository,
                                   ServiceRepository serviceRepository,
                                   TherapistRepository therapistRepository,
                                   RoomRepository roomRepository,
                                   OrderRepository orderRepository,
                                   ShiftRepository shiftRepository,
                                   ShiftAssignmentRepository shiftAssignmentRepository,
                                   OrderItemAttendeeRepository attendeeRepository) {
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.slipRepository = slipRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
        this.roomRepository = roomRepository;
        this.orderRepository = orderRepository;
        this.shiftRepository = shiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.attendeeRepository = attendeeRepository;
    }

    public record ServiceLine(Long serviceId, String serviceName, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record CutoffServicesReport(OffsetDateTime from, OffsetDateTime to, List<ServiceLine> lines, BigDecimal grandTotal) {}

    public record DailyRow(
            String checkInTime,
            String orNumber,
            String lockerNumber,
            String packageName,
            String treatment,
            BigDecimal amount,
            String tsn,
            String therapist,
            String room,
            String massageStart,
            String massageEnd,
            String status
    ) {}
    public record DailyReportResponse(LocalDate date, List<DailyRow> rows, BigDecimal grandTotal) {}
    public record MonthlyReportResponse(int year, int month, List<DailyRow> rows, BigDecimal grandTotal) {}

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public CutoffServicesReport cutoffServices(UUID organizationId, OffsetDateTime from, OffsetDateTime to, Long shiftId) {
        List<TreatmentSlip> slips = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to)
                .stream()
                .filter(s -> !"VOIDED".equals(s.getStatus()))
                .collect(Collectors.toList());

        if (shiftId != null) {
            Set<UUID> assignedTherapistIds = new HashSet<>();
            for (ShiftAssignment sa : shiftAssignmentRepository.findAllByShiftId(shiftId)) {
                assignedTherapistIds.add(sa.getTherapistId());
            }

            slips = slips.stream().filter(slip -> {
                UUID primaryId = resolveTherapistId(slip.getPrimaryTherapistNickname(), slip.getSessionId());
                UUID secondaryId = resolveTherapistId(slip.getSecondaryTherapistNickname(), null);
                return assignedTherapistIds.contains(primaryId) || assignedTherapistIds.contains(secondaryId);
            }).collect(Collectors.toList());
        }

        Map<UUID, UUID> slipToOrderItemId = resolveSlipToOrderItem(slips);
        Map<String, ServiceAccumulator> bucket = new HashMap<>();
        Set<UUID> countedOrderItemIds = new HashSet<>();
        for (TreatmentSlip s : slips) {
            String pkg = s.getPackageName() != null ? s.getPackageName() : "(Walk-in)";
            String svc = s.getServiceName() == null ? "Unknown" : s.getServiceName();
            String key = pkg + " — " + svc;
            UUID orderItemId = slipToOrderItemId.get(s.getId());
            ServiceAccumulator acc = bucket.computeIfAbsent(key, k -> new ServiceAccumulator());
            if (orderItemId != null) {
                if (countedOrderItemIds.add(orderItemId)) {
                    acc.qty++;
                    if (s.getTotalAmount() != null) {
                        acc.total = acc.total.add(s.getTotalAmount());
                    }
                }
            } else {
                acc.qty++;
                if (s.getTotalAmount() != null) {
                    acc.total = acc.total.add(s.getTotalAmount());
                }
            }
        }
        List<ServiceLine> lines = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Map.Entry<String, ServiceAccumulator> e : bucket.entrySet()) {
            ServiceAccumulator acc = e.getValue();
            BigDecimal unit = acc.qty == 0 ? BigDecimal.ZERO
                    : acc.total.divide(BigDecimal.valueOf(acc.qty), 2, java.math.RoundingMode.HALF_UP);
            lines.add(new ServiceLine(null, e.getKey(), acc.qty, unit, acc.total));
            grand = grand.add(acc.total);
        }
        lines.sort((a, b) -> b.lineTotal().compareTo(a.lineTotal()));
        return new CutoffServicesReport(from, to, lines, grand);
    }

    private Map<UUID, UUID> resolveSlipToOrderItem(List<TreatmentSlip> slips) {
        Map<UUID, UUID> out = new HashMap<>();
        Set<UUID> attendeeIds = new HashSet<>();
        for (TreatmentSlip s : slips) {
            if (s.getAttendeeId() != null) attendeeIds.add(s.getAttendeeId());
        }
        if (attendeeIds.isEmpty()) return out;
        Map<UUID, UUID> attendeeToOrderItem = new HashMap<>();
        for (OrderItemAttendee a : attendeeRepository.findAllById(attendeeIds)) {
            attendeeToOrderItem.put(a.getId(), a.getOrderItemId());
        }
        for (TreatmentSlip s : slips) {
            UUID a = s.getAttendeeId();
            if (a != null) {
                UUID oid = attendeeToOrderItem.get(a);
                if (oid != null) out.put(s.getId(), oid);
            }
        }
        return out;
    }

    public byte[] cutoffServicesCsv(UUID organizationId, OffsetDateTime from, OffsetDateTime to, Long shiftId) {
        CutoffServicesReport report = cutoffServices(organizationId, from, to, shiftId);
        StringBuilder sb = new StringBuilder();
        sb.append("Service,Qty,Unit Price,Line Total\n");
        for (ServiceLine l : report.lines()) {
            sb.append(csvCell(l.serviceName())).append(',')
              .append(l.qty()).append(',')
              .append(l.unitPrice() != null ? l.unitPrice().toPlainString() : "0").append(',')
              .append(l.lineTotal() != null ? l.lineTotal().toPlainString() : "0").append('\n');
        }
        sb.append("\n,,GRAND TOTAL,").append(report.grandTotal().toPlainString()).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public DailyReportResponse daily(UUID organizationId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(java.time.ZoneId.of("Asia/Manila")).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);
        List<DailyRow> rows = collectRows(organizationId, from, to);
        BigDecimal grand = rows.stream().map(r -> r.amount() == null ? BigDecimal.ZERO : r.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailyReportResponse(date, rows, grand);
    }

    public byte[] dailyCsv(UUID organizationId, LocalDate date) {
        DailyReportResponse r = daily(organizationId, date);
        return rowsToCsv(r.rows(), r.grandTotal());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public MonthlyReportResponse monthly(UUID organizationId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        OffsetDateTime from = ym.atDay(1).atStartOfDay(java.time.ZoneId.of("Asia/Manila")).toOffsetDateTime();
        OffsetDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Manila")).toOffsetDateTime();
        List<DailyRow> rows = collectRows(organizationId, from, to);
        BigDecimal grand = rows.stream().map(r -> r.amount() == null ? BigDecimal.ZERO : r.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlyReportResponse(year, month, rows, grand);
    }

    public byte[] monthlyCsv(UUID organizationId, int year, int month) {
        MonthlyReportResponse r = monthly(organizationId, year, month);
        return rowsToCsv(r.rows(), r.grandTotal());
    }

    private List<DailyRow> collectRows(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        List<TreatmentSlip> slips = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to)
                .stream()
                .filter(s -> !"VOIDED".equals(s.getStatus()))
                .collect(Collectors.toList());
        if (slips.isEmpty()) return Collections.emptyList();

        Map<UUID, Booking> bookingById = new HashMap<>();
        Map<UUID, Session> sessionById = new HashMap<>();
        Map<UUID, Order> orderByBooking = orderRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(o -> o.getBookingId() != null)
                .collect(Collectors.toMap(Order::getBookingId, o -> o, (a, b) -> a));

        Map<UUID, UUID> slipToOrderItemId = resolveSlipToOrderItem(slips);

        Map<UUID, List<TreatmentSlip>> groupedByOrderItem = new HashMap<>();
        List<TreatmentSlip> orphanSlips = new ArrayList<>();
        for (TreatmentSlip slip : slips) {
            UUID oid = slipToOrderItemId.get(slip.getId());
            if (oid != null) {
                groupedByOrderItem.computeIfAbsent(oid, k -> new ArrayList<>()).add(slip);
            } else {
                orphanSlips.add(slip);
            }
        }

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        List<DailyRow> rows = new ArrayList<>();

        for (Map.Entry<UUID, List<TreatmentSlip>> e : groupedByOrderItem.entrySet()) {
            List<TreatmentSlip> group = e.getValue();
            group.sort((x, y) -> {
                OffsetDateTime cx = x.getCreatedAt();
                OffsetDateTime cy = y.getCreatedAt();
                if (cx == null && cy == null) return 0;
                if (cx == null) return 1;
                if (cy == null) return -1;
                return cx.compareTo(cy);
            });
            TreatmentSlip head = group.get(0);
            Booking b = head.getBookingId() == null ? null
                    : bookingById.computeIfAbsent(head.getBookingId(),
                        id -> bookingRepository.findById(id).orElse(null));
            if (b != null && "CANCELLED".equals(b.getStatus())) continue;
            Order o = b == null ? null : orderByBooking.get(b.getId());
            if (o != null && "CANCELLED".equals(o.getStatus())) continue;
            DailyRow row = buildRow(head, group, b, o, sessionById, timeFmt, true);
            rows.add(row);
        }

        for (TreatmentSlip slip : orphanSlips) {
            Booking b = slip.getBookingId() == null ? null
                    : bookingById.computeIfAbsent(slip.getBookingId(),
                        id -> bookingRepository.findById(id).orElse(null));
            if (b != null && "CANCELLED".equals(b.getStatus())) continue;
            Order o = b == null ? null : orderByBooking.get(b.getId());
            if (o != null && "CANCELLED".equals(o.getStatus())) continue;
            DailyRow row = buildRow(slip, List.of(slip), b, o, sessionById, timeFmt, false);
            rows.add(row);
        }

        rows.sort((a, b) -> {
            String ka = a.checkInTime() == null ? "" : a.checkInTime();
            String kb = b.checkInTime() == null ? "" : b.checkInTime();
            return ka.compareTo(kb);
        });
        return rows;
    }

    private DailyRow buildRow(TreatmentSlip head, List<TreatmentSlip> group, Booking b, Order o,
                              Map<UUID, Session> sessionById, DateTimeFormatter timeFmt, boolean grouped) {
        Session s = head.getSessionId() == null ? null
                : sessionById.computeIfAbsent(head.getSessionId(),
                    id -> sessionRepository.findById(id).orElse(null));

        String checkIn;
        if (s != null && s.getStartedAt() != null) {
            checkIn = formatTime(s.getStartedAt(), timeFmt);
        } else if (b != null && b.getActualStartAt() != null) {
            checkIn = formatTime(b.getActualStartAt(), timeFmt);
        } else if (b != null) {
            checkIn = formatTime(b.getScheduledAt(), timeFmt);
        } else {
            checkIn = formatTime(head.getCreatedAt(), timeFmt);
        }
        String orNumber = head.getOrNumber() != null ? head.getOrNumber()
                : (o != null && o.getOrNumber() != null ? o.getOrNumber() : "");

        String locker;
        if (grouped && group.size() > 1) {
            locker = group.stream()
                    .map(TreatmentSlip::getLockerNumber)
                    .filter(l -> l != null && !l.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (locker.isBlank() && b != null && b.getLockerNumber() != null) {
                locker = b.getLockerNumber();
            }
        } else {
            locker = head.getLockerNumber() != null ? head.getLockerNumber()
                    : (b != null && b.getLockerNumber() != null ? b.getLockerNumber() : "");
        }

        String packageName = head.getPackageName();
        String treatment = head.getServiceName() == null ? "" : head.getServiceName();

        BigDecimal amount = head.getTotalAmount() != null ? head.getTotalAmount() : BigDecimal.ZERO;

        String tsn = head.getTsn() == null ? "" : head.getTsn();

        String therapist = "";
        if (s != null && s.getPrimaryTherapistId() != null) {
            therapist = therapistRepository.findById(s.getPrimaryTherapistId())
                    .map(t -> t.getNickname()).orElse("");
        } else if (head.getPrimaryTherapistNickname() != null) {
            therapist = head.getPrimaryTherapistNickname();
        }
        String room = "";
        if (s != null && s.getRoomId() != null) {
            room = roomRepository.findById(s.getRoomId()).map(r -> r.getRoomNumber()).orElse("");
        } else if (head.getRoomNumber() != null) {
            room = head.getRoomNumber();
        }
        String start = formatTime(s != null ? s.getStartedAt() : head.getStartTime(), timeFmt);
        String end = formatTime(s != null ? s.getEndedAt() : head.getEndTime(), timeFmt);
        String status = s != null ? s.getStatus() : head.getStatus();
        return new DailyRow(checkIn, orNumber, locker, packageName, treatment, amount, tsn, therapist, room, start, end, status);
    }

    private UUID resolveTherapistId(String nickname, UUID sessionId) {
        if (sessionId != null) {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && session.getPrimaryTherapistId() != null) {
                return session.getPrimaryTherapistId();
            }
        }
        if (nickname == null || nickname.isBlank()) return null;
        return therapistRepository.findAll().stream()
                .filter(t -> nickname.equalsIgnoreCase(t.getNickname()))
                .map(t -> t.getId())
                .findFirst().orElse(null);
    }

    private byte[] rowsToCsv(List<DailyRow> rows, BigDecimal grandTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Check-in Time,OR#,Locker#,Package,Treatment,Amount,TS#,Therapist,Room,Massage Start,Massage End,Status\n");
        for (DailyRow r : rows) {
            sb.append(csvCell(r.checkInTime())).append(',')
              .append(csvCell(r.orNumber())).append(',')
              .append(csvCell(r.lockerNumber())).append(',')
              .append(csvCell(r.packageName())).append(',')
              .append(csvCell(r.treatment())).append(',')
              .append(r.amount() != null ? r.amount().toPlainString() : "0").append(',')
              .append(csvCell(r.tsn())).append(',')
              .append(csvCell(r.therapist())).append(',')
              .append(csvCell(r.room())).append(',')
              .append(csvCell(r.massageStart())).append(',')
              .append(csvCell(r.massageEnd())).append(',')
              .append(csvCell(r.status())).append('\n');
        }
        sb.append("\n,,,,,").append(grandTotal != null ? grandTotal.toPlainString() : "0")
          .append(",,GRAND TOTAL,,,\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvCell(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String formatTime(OffsetDateTime dt, DateTimeFormatter fmt) {
        if (dt == null) return "";
        return dt.atZoneSameInstant(java.time.ZoneId.of("Asia/Manila")).format(fmt);
    }

    private static class ServiceAccumulator {
        int qty = 0;
        BigDecimal total = BigDecimal.ZERO;

        void accept(TreatmentSlip slip) {
            qty++;
            if (slip.getTotalAmount() != null) {
                total = total.add(slip.getTotalAmount());
            }
        }
    }
}
