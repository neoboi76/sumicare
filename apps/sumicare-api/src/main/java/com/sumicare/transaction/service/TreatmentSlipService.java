package com.sumicare.transaction.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private String generateTsn() {
        return "TS" + System.currentTimeMillis() % 100000;
    }
}
