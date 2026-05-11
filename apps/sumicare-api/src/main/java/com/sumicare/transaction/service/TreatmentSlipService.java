package com.sumicare.transaction.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.dto.UpdateTreatmentSlipRequest;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public byte[] exportToCsv(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        List<TreatmentSlip> slips = slipRepository.findAllByOrganizationIdAndCreatedAtBetween(organizationId, from, to);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("Slip Type,TSN,Date,Customer,Locker,Room,Therapist,Secondary Therapist,Requested Therapist,")
          .append("Service,Service Duration (min),Jacuzzi (min),Massage (min),Pax,Wine,")
          .append("Start,End,OR#,Add-on OR#,Others/Add-on,Remarks,Total,Waiver,Generated At\n");
        for (TreatmentSlip s : slips) {
            sb.append(csvCell(s.isVip() ? "VIP" : "Regular")).append(',')
              .append(csvCell(s.getTsn())).append(',')
              .append(csvCell(s.getCreatedAt() != null ? s.getCreatedAt().format(fmt) : "")).append(',')
              .append(csvCell(s.getClientNickname())).append(',')
              .append(csvCell(s.getLockerNumber())).append(',')
              .append(csvCell(s.getRoomNumber())).append(',')
              .append(csvCell(s.getPrimaryTherapistNickname())).append(',')
              .append(csvCell(s.getSecondaryTherapistNickname())).append(',')
              .append(csvCell(s.getRequestedTherapistNickname())).append(',')
              .append(csvCell(s.getServiceName())).append(',')
              .append(s.getTreatmentMinutes() != null ? s.getTreatmentMinutes() : "").append(',')
              .append(s.getJacuzziMinutes() != null ? s.getJacuzziMinutes() : "").append(',')
              .append(s.getMassageMinutes() != null ? s.getMassageMinutes() : "").append(',')
              .append(s.getPax() != null ? s.getPax() : "").append(',')
              .append(s.getWineIncluded() == null ? "" : (s.getWineIncluded() ? "Yes" : "No")).append(',')
              .append(csvCell(s.getStartTime() != null ? s.getStartTime().format(fmt) : "")).append(',')
              .append(csvCell(s.getEndTime() != null ? s.getEndTime().format(fmt) : "")).append(',')
              .append(csvCell(s.getOrNumber())).append(',')
              .append(csvCell(s.getAddOnOrNumber())).append(',')
              .append(csvCell(s.getOthersAddOn())).append(',')
              .append(csvCell(s.getRemarks())).append(',')
              .append(s.getTotalAmount() != null ? s.getTotalAmount().toPlainString() : "").append(',')
              .append(s.isWaiverAccepted() ? "Yes" : "No").append(',')
              .append(csvCell(s.getCreatedAt() != null ? s.getCreatedAt().format(fmt) : ""))
              .append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String generateTsn() {
        return "TS" + System.currentTimeMillis() % 100000;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public TreatmentSlip update(UUID organizationId, UUID slipId, UpdateTreatmentSlipRequest request) {
        TreatmentSlip slip = slipRepository.findById(slipId).orElseThrow();
        if (!slip.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Slip not in organization");
        }
        if (!"DRAFT".equals(slip.getStatus())) {
            throw new IllegalStateException("Treatment slip is " + slip.getStatus().toLowerCase() + " and cannot be edited");
        }
        if (request.lockerNumber() != null) slip.setLockerNumber(request.lockerNumber());
        if (request.roomNumber() != null) slip.setRoomNumber(request.roomNumber());
        if (request.othersAddOn() != null) slip.setOthersAddOn(request.othersAddOn());
        if (request.remarks() != null) slip.setRemarks(request.remarks());
        if (request.orNumber() != null) slip.setOrNumber(request.orNumber());
        if (request.addOnOrNumber() != null) slip.setAddOnOrNumber(request.addOnOrNumber());
        if (request.totalAmount() != null) slip.setTotalAmount(request.totalAmount());
        if (request.jacuzziMinutes() != null) slip.setJacuzziMinutes(request.jacuzziMinutes());
        if (request.massageMinutes() != null) slip.setMassageMinutes(request.massageMinutes());
        if (request.wineIncluded() != null) slip.setWineIncluded(request.wineIncluded());
        if (request.waiverAccepted() != null && request.waiverAccepted() && !slip.isWaiverAccepted()) {
            slip.setWaiverAccepted(true);
            slip.setWaiverAcceptedAt(OffsetDateTime.now());
        }
        return slipRepository.save(slip);
    }
}
