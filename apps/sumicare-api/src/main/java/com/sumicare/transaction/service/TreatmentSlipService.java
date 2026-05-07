package com.sumicare.transaction.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentSlipService {

    private final TreatmentSlipRepository slipRepository;
    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final ServiceRepository serviceRepository;
    private final TherapistRepository therapistRepository;

    public TreatmentSlipService(TreatmentSlipRepository slipRepository,
                                BookingRepository bookingRepository,
                                SessionRepository sessionRepository,
                                ServiceRepository serviceRepository,
                                TherapistRepository therapistRepository) {
        this.slipRepository = slipRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public TreatmentSlip generateForSession(UUID organizationId, UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        var service = serviceRepository.findById(booking.getServiceId()).orElseThrow();
        TreatmentSlip slip = new TreatmentSlip();
        slip.setOrganizationId(organizationId);
        slip.setBookingId(booking.getId());
        slip.setSessionId(session.getId());
        slip.setTsn(generateTsn());
        slip.setClientNickname(booking.getClientNickname());
        slip.setLockerNumber(booking.getLockerNumber());
        slip.setServiceName(service.getName());
        slip.setStartTime(session.getStartedAt());
        slip.setEndTime(session.getEndedAt());
        slip.setVip(service.isVip());
        slip.setPax(booking.getPax());
        if (!service.isVip()) {
            slip.setTreatmentMinutes(service.getDurationMinutes());
        }
        if (session.getPrimaryTherapistId() != null) {
            therapistRepository.findById(session.getPrimaryTherapistId())
                    .ifPresent(t -> slip.setPrimaryTherapistNickname(t.getNickname()));
        }
        if (session.getSecondaryTherapistId() != null) {
            therapistRepository.findById(session.getSecondaryTherapistId())
                    .ifPresent(t -> slip.setSecondaryTherapistNickname(t.getNickname()));
        }
        if (session.isSpecificallyRequested() && session.getPrimaryTherapistId() != null) {
            therapistRepository.findById(session.getPrimaryTherapistId())
                    .ifPresent(t -> slip.setRequestedTherapistNickname(t.getNickname()));
        }
        return slipRepository.save(slip);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public byte[] exportToExcel(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        List<TreatmentSlip> slips = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet regular = wb.createSheet("Regular slips");
            Sheet vip = wb.createSheet("VIP slips");
            String[] headers = {"TSN", "Date", "Customer", "Locker", "Room", "Therapist",
                    "Service", "Treatment min", "Jacuzzi min", "Massage min", "Pax", "Wine",
                    "Start", "End", "OR#", "Others/Add-on", "Add-on OR#", "Remarks", "Total",
                    "Waiver", "Created"};
            writeHeaderRow(regular, headers, wb);
            writeHeaderRow(vip, headers, wb);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (TreatmentSlip s : slips) {
                Sheet sheet = s.isVip() ? vip : regular;
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                int c = 0;
                row.createCell(c++).setCellValue(s.getTsn());
                row.createCell(c++).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(fmt) : "");
                row.createCell(c++).setCellValue(nullToEmpty(s.getClientNickname()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getLockerNumber()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getRoomNumber()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getPrimaryTherapistNickname()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getServiceName()));
                row.createCell(c++).setCellValue(s.getTreatmentMinutes() != null ? s.getTreatmentMinutes() : 0);
                row.createCell(c++).setCellValue(s.getJacuzziMinutes() != null ? s.getJacuzziMinutes() : 0);
                row.createCell(c++).setCellValue(s.getMassageMinutes() != null ? s.getMassageMinutes() : 0);
                row.createCell(c++).setCellValue(s.getPax() != null ? s.getPax() : 0);
                row.createCell(c++).setCellValue(s.getWineIncluded() == null ? "" : (s.getWineIncluded() ? "Yes" : "No"));
                row.createCell(c++).setCellValue(s.getStartTime() != null ? s.getStartTime().format(fmt) : "");
                row.createCell(c++).setCellValue(s.getEndTime() != null ? s.getEndTime().format(fmt) : "");
                row.createCell(c++).setCellValue(nullToEmpty(s.getOrNumber()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getOthersAddOn()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getAddOnOrNumber()));
                row.createCell(c++).setCellValue(nullToEmpty(s.getRemarks()));
                row.createCell(c++).setCellValue(s.getTotalAmount() != null ? s.getTotalAmount().doubleValue() : 0);
                row.createCell(c++).setCellValue(s.isWaiverAccepted() ? "Yes" : "No");
                row.createCell(c).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(fmt) : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Treatment slip Excel export failed", e);
        }
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, Workbook wb) {
        Row row = sheet.createRow(0);
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String generateTsn() {
        return "TS" + System.currentTimeMillis() % 100000;
    }
}
