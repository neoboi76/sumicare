package com.sumicare.report.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public OperationsReportService(SessionRepository sessionRepository,
                                   BookingRepository bookingRepository,
                                   TreatmentSlipRepository slipRepository,
                                   ServiceRepository serviceRepository,
                                   TherapistRepository therapistRepository,
                                   RoomRepository roomRepository,
                                   OrderRepository orderRepository,
                                   ShiftRepository shiftRepository) {
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.slipRepository = slipRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
        this.roomRepository = roomRepository;
        this.orderRepository = orderRepository;
        this.shiftRepository = shiftRepository;
    }

    public record ServiceLine(Long serviceId, String serviceName, int qty, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record CutoffServicesReport(OffsetDateTime from, OffsetDateTime to, List<ServiceLine> lines, BigDecimal grandTotal) {}

    public record DailyRow(
            String checkInTime,
            String orNumber,
            String lockerNumber,
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
        List<TreatmentSlip> slips = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);

        // If shiftId is provided, filter slips by the shift's time window
        if (shiftId != null) {
            var shift = shiftRepository.findById(shiftId).orElse(null);
            if (shift != null) {
                java.time.LocalTime shiftStart = shift.getStartTime();
                java.time.LocalTime shiftEnd = shift.getEndTime();
                slips = slips.stream().filter(slip -> {
                    if (slip.getStartTime() == null) return false;
                    java.time.LocalTime slipTime = slip.getStartTime().toLocalTime();
                    if (shiftStart.isBefore(shiftEnd)) {
                        return !slipTime.isBefore(shiftStart) && slipTime.isBefore(shiftEnd);
                    } else {
                        // Overnight shift (e.g. 22:00 - 06:00)
                        return !slipTime.isBefore(shiftStart) || slipTime.isBefore(shiftEnd);
                    }
                }).toList();
            }
        }

        Map<String, ServiceAccumulator> bucket = new HashMap<>();
        for (TreatmentSlip s : slips) {
            String key = s.getServiceName() == null ? "Unknown" : s.getServiceName();
            bucket.computeIfAbsent(key, k -> new ServiceAccumulator()).accept(s);
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
        List<Booking> bookings = bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(organizationId, from, to);
        if (bookings.isEmpty()) return Collections.emptyList();

        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<UUID, Session> sessionByBooking = sessionRepository.findAll().stream()
                .filter(s -> bookingIds.contains(s.getBookingId()))
                .collect(Collectors.toMap(Session::getBookingId, s -> s, (a, b) -> a));
        Map<UUID, TreatmentSlip> slipByBooking = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId,
                from.minusDays(1), to.plusDays(1)).stream()
                .filter(s -> bookingIds.contains(s.getBookingId()))
                .collect(Collectors.toMap(TreatmentSlip::getBookingId, s -> s, (a, b) -> a));
        Map<UUID, Order> orderByBooking = orderRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(o -> bookingIds.contains(o.getBookingId()))
                .collect(Collectors.toMap(Order::getBookingId, o -> o, (a, b) -> a));

        Map<Long, Service> servicesById = new HashMap<>();
        for (Booking b : bookings) {
            servicesById.computeIfAbsent(b.getServiceId(),
                    id -> serviceRepository.findById(id).orElse(null));
        }

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        List<DailyRow> rows = new ArrayList<>();
        for (Booking b : bookings) {
            Session s = sessionByBooking.get(b.getId());
            TreatmentSlip slip = slipByBooking.get(b.getId());
            Order o = orderByBooking.get(b.getId());
            Service svc = servicesById.get(b.getServiceId());

            String checkIn = b.getActualStartAt() != null
                    ? b.getActualStartAt().format(timeFmt)
                    : (b.getScheduledAt() != null ? b.getScheduledAt().format(timeFmt) : "");
            String orNumber = o != null && o.getOrNumber() != null ? o.getOrNumber()
                    : (slip != null ? slip.getOrNumber() : "");
            String locker = b.getLockerNumber() != null ? b.getLockerNumber() : (slip != null ? slip.getLockerNumber() : "");
            String treatment = svc != null ? svc.getName() : (slip != null ? slip.getServiceName() : "");
            BigDecimal amount = o != null ? o.getTotal() : (slip != null && slip.getTotalAmount() != null ? slip.getTotalAmount() : BigDecimal.ZERO);
            String tsn = slip != null ? slip.getTsn() : "";
            String therapist = "";
            if (s != null && s.getPrimaryTherapistId() != null) {
                therapist = therapistRepository.findById(s.getPrimaryTherapistId())
                        .map(t -> t.getNickname()).orElse("");
            } else if (slip != null) {
                therapist = slip.getPrimaryTherapistNickname() == null ? "" : slip.getPrimaryTherapistNickname();
            }
            String room = "";
            if (s != null && s.getRoomId() != null) {
                room = roomRepository.findById(s.getRoomId()).map(r -> r.getRoomNumber()).orElse("");
            } else if (slip != null) {
                room = slip.getRoomNumber() == null ? "" : slip.getRoomNumber();
            }
            String start = s != null && s.getStartedAt() != null ? s.getStartedAt().format(timeFmt) : "";
            String end = s != null && s.getEndedAt() != null ? s.getEndedAt().format(timeFmt) : "";
            String status = b.getStatus();
            rows.add(new DailyRow(checkIn, orNumber, locker, treatment, amount, tsn, therapist, room, start, end, status));
        }
        rows.sort((a, b) -> {
            String ka = a.checkInTime() == null ? "" : a.checkInTime();
            String kb = b.checkInTime() == null ? "" : b.checkInTime();
            return ka.compareTo(kb);
        });
        return rows;
    }

    private byte[] rowsToCsv(List<DailyRow> rows, BigDecimal grandTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Check-in Time,OR#,Locker#,Treatment,Amount,TS#,Therapist,Room,Massage Start,Massage End,Status\n");
        for (DailyRow r : rows) {
            sb.append(csvCell(r.checkInTime())).append(',')
              .append(csvCell(r.orNumber())).append(',')
              .append(csvCell(r.lockerNumber())).append(',')
              .append(csvCell(r.treatment())).append(',')
              .append(r.amount() != null ? r.amount().toPlainString() : "0").append(',')
              .append(csvCell(r.tsn())).append(',')
              .append(csvCell(r.therapist())).append(',')
              .append(csvCell(r.room())).append(',')
              .append(csvCell(r.massageStart())).append(',')
              .append(csvCell(r.massageEnd())).append(',')
              .append(csvCell(r.status())).append('\n');
        }
        sb.append("\n,,,,").append(grandTotal != null ? grandTotal.toPlainString() : "0")
          .append(",,GRAND TOTAL,,,,\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvCell(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
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
