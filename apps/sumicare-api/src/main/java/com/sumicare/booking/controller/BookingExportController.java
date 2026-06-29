/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.report.service.ExcelExportService;
import com.sumicare.user.repository.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingExportController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final ExcelExportService excelExportService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public BookingExportController(BookingRepository bookingRepository,
                                   SessionRepository sessionRepository,
                                   ExcelExportService excelExportService,
                                   OrganizationRepository organizationRepository,
                                   UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.excelExportService = excelExportService;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam OffsetDateTime from,
                                             @RequestParam OffsetDateTime to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<Booking> bookings = bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(orgId, from, to);

        Set<UUID> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toSet());
        List<Session> allSessions = bookingIds.isEmpty()
                ? List.of()
                : sessionRepository.findAllByBookingIdIn(bookingIds);
        Map<UUID, Session> sessionByBookingId = allSessions.stream()
                .filter(s -> s.getBookingId() != null)
                .collect(Collectors.toMap(Session::getBookingId, s -> s, (a, b) -> a));

        String logoUrl = organizationRepository.findById(orgId).map(o -> o.getLogoUrl()).orElse(null);
        String preparedBy = userRepository.findById(UUID.fromString(principal.userId()))
                .map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
                .orElse("Staff");

        LocalDate fromDate = from.atZoneSameInstant(MANILA).toLocalDate();
        LocalDate toDate = to.atZoneSameInstant(MANILA).toLocalDate();
        String range = fromDate + " to " + toDate;

        ExcelExportService.WorkbookContext ctx = excelExportService.createWorkbook(
                "Bookings Export", "Bookings Export", range, preparedBy, logoUrl);

        excelExportService.writeHeaderRow(ctx, List.of(
                "Appointment Date", "Booking ID", "Status", "Reservation Type", "Client Nickname",
                "Sex", "Pax", "Service ID", "Scheduled At", "Start Time",
                "End Time (Expected)", "Ended At (Actual)",
                "Primary Therapist ID", "Secondary Therapist ID", "Room ID", "Bed ID", "Locker"));

        Map<LocalDate, List<Booking>> byDate = new LinkedHashMap<>();
        for (Booking b : bookings) {
            LocalDate appointmentDate = b.getScheduledAt() != null
                    ? b.getScheduledAt().atZoneSameInstant(MANILA).toLocalDate()
                    : fromDate;
            byDate.computeIfAbsent(appointmentDate, k -> new ArrayList<>()).add(b);
        }

        for (Map.Entry<LocalDate, List<Booking>> dateEntry : byDate.entrySet()) {
            for (Booking b : dateEntry.getValue()) {
                Session s = sessionByBookingId.get(b.getId());
                excelExportService.writeDataRow(ctx, List.of(
                        dateEntry.getKey().toString(),
                        b.getId().toString(),
                        b.getStatus() != null ? b.getStatus() : "",
                        b.getReservationType() != null ? b.getReservationType() : "",
                        b.getClientNickname() != null ? b.getClientNickname() : "",
                        b.getClientGender() != null ? b.getClientGender() : "",
                        b.getPax() != null ? b.getPax() : 0,
                        b.getServiceId() != null ? b.getServiceId().toString() : "",
                        b.getScheduledAt() != null ? b.getScheduledAt().atZoneSameInstant(MANILA).format(FMT) : "",
                        s != null && s.getStartedAt() != null ? s.getStartedAt().atZoneSameInstant(MANILA).format(FMT) : "",
                        s != null && s.getExpectedEndAt() != null ? s.getExpectedEndAt().atZoneSameInstant(MANILA).format(FMT) : "",
                        s != null && s.getEndedAt() != null ? s.getEndedAt().atZoneSameInstant(MANILA).format(FMT) : "",
                        s != null && s.getPrimaryTherapistId() != null ? s.getPrimaryTherapistId().toString() : "",
                        s != null && s.getSecondaryTherapistId() != null ? s.getSecondaryTherapistId().toString() : "",
                        s != null && s.getRoomId() != null ? s.getRoomId().toString() : "",
                        s != null && s.getBedId() != null ? s.getBedId().toString() : "",
                        b.getLockerNumber() != null ? b.getLockerNumber() : ""));
            }
        }

        excelExportService.writeTotalRow(ctx, List.of(
                "Total Bookings: " + bookings.size(),
                "", "", "", "", "", BigDecimal.ZERO, "", "", "", "", "", "", "", "", "", ""));
        excelExportService.writeFooter(ctx, 17);
        excelExportService.autoSizeColumns(ctx, 17);
        byte[] data = excelExportService.toBytes(ctx.workbook);

        String filename = "bookings-" + fromDate + "-to-" + toDate + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(data);
    }
}
