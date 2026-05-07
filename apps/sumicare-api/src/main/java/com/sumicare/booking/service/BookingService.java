package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.SessionResponse;
import com.sumicare.booking.dto.StartSessionRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.room.service.RoomOccupancyService;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.DeckingService;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class BookingService {

    private static final int PREP_BUFFER_MINUTES = 15;

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final ServiceRepository serviceRepository;
    private final TherapistRepository therapistRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final RoomOccupancyService occupancyService;
    private final DeckingService deckingService;
    private final NotificationService notificationService;
    private final TreatmentSlipRepository slipRepository;

    public BookingService(BookingRepository bookingRepository, SessionRepository sessionRepository,
                          ServiceRepository serviceRepository, TherapistRepository therapistRepository,
                          RoomRepository roomRepository, BedRepository bedRepository,
                          RoomOccupancyService occupancyService, DeckingService deckingService,
                          NotificationService notificationService,
                          TreatmentSlipRepository slipRepository) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.occupancyService = occupancyService;
        this.deckingService = deckingService;
        this.notificationService = notificationService;
        this.slipRepository = slipRepository;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public BookingResponse createBooking(UUID organizationId, CreateBookingRequest request) {
        Service service = requireService(request.serviceId());
        Booking booking = new Booking();
        booking.setOrganizationId(organizationId);
        booking.setClientId(request.clientId());
        booking.setClientNickname(request.clientNickname());
        booking.setLockerNumber(request.lockerNumber());
        booking.setServiceId(request.serviceId());
        booking.setReservationType(request.reservationType());
        booking.setScheduledAt(request.scheduledAt());
        booking.setPax(request.pax());
        booking.setClientGender(request.clientGender());
        booking.setStatus("PENDING");
        bookingRepository.save(booking);
        return toBookingResponse(booking, service);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse startSession(UUID organizationId, UUID bookingId, StartSessionRequest request) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Service service = requireService(booking.getServiceId());
        Session session = new Session();
        session.setOrganizationId(organizationId);
        session.setBookingId(booking.getId());
        session.setPrimaryTherapistId(request.primaryTherapistId());
        session.setSecondaryTherapistId(request.secondaryTherapistId());
        session.setRoomId(request.roomId());
        session.setBedId(request.bedId());
        session.setSpecificallyRequested(request.specificallyRequested());

        OffsetDateTime now = OffsetDateTime.now();
        session.setStartedAt(now);
        session.setExpectedEndAt(now.plusMinutes(service.getDurationMinutes()));
        session.setStatus("ACTIVE");
        sessionRepository.save(session);

        booking.setActualStartAt(session.getStartedAt());
        booking.setStatus("ACTIVE");

        if (request.roomId() != null && request.bedId() != null) {
            Therapist primary = request.primaryTherapistId() == null ? null
                    : therapistRepository.findById(request.primaryTherapistId()).orElse(null);
            String therapistNickname = primary == null ? "" : primary.getNickname();
            occupancyService.occupy(organizationId, request.roomId(), request.bedId(),
                    booking.getClientNickname(), booking.getLockerNumber(),
                    therapistNickname, null);
        }

        if (request.primaryTherapistId() != null) {
            if (request.specificallyRequested()) {
                deckingService.servedRequested(organizationId, request.primaryTherapistId());
            } else {
                deckingService.rotateToBack(organizationId, request.primaryTherapistId());
            }
        }

        if (request.roomId() != null && request.bedId() != null) {
            notificationService.broadcastRoomUpdate(organizationId, request.roomId(), request.bedId(),
                    Map.of("event", "SESSION_STARTED", "sessionId", session.getId()));
        }

        return toSessionResponse(session);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse endSession(UUID organizationId, UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        session.setEndedAt(now);
        session.setStatus("COMPLETED");
        if (session.getRoomId() != null && session.getBedId() != null) {
            occupancyService.release(organizationId, session.getRoomId(), session.getBedId());
        }
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        booking.setActualEndAt(now);
        booking.setStatus("COMPLETED");

        slipRepository.findBySessionId(sessionId).ifPresent(slip -> {
            slip.setEndTime(now);
            slipRepository.save(slip);
        });

        if (session.getRoomId() != null && session.getBedId() != null) {
            notificationService.broadcastRoomUpdate(organizationId, session.getRoomId(), session.getBedId(),
                    Map.of("event", "SESSION_ENDED", "sessionId", session.getId()));
        }

        return toSessionResponse(session);
    }

    @Transactional
    public void autoEndSession(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if ("ACTIVE".equals(session.getStatus())) {
                endSession(session.getOrganizationId(), sessionId);
            }
        });
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse extendSession(UUID sessionId, int additionalMinutes) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        session.setExtension(true);
        session.setExtensionMinutes(session.getExtensionMinutes() + additionalMinutes);
        if (session.getExpectedEndAt() != null) {
            session.setExpectedEndAt(session.getExpectedEndAt().plusMinutes(additionalMinutes));
        }
        return toSessionResponse(session);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse adjustTimes(UUID sessionId, OffsetDateTime startAt, OffsetDateTime endAt) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        if (startAt != null) session.setStartedAt(startAt);
        if (endAt != null) {
            session.setEndedAt(endAt);
            session.setExpectedEndAt(endAt);
        }
        return toSessionResponse(session);
    }

    public List<BookingResponse> listBookingsForDay(UUID organizationId, OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        return bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(organizationId, dayStart, dayEnd)
                .stream()
                .map(b -> toBookingResponse(b, requireService(b.getServiceId())))
                .toList();
    }

    public List<Session> findExpiredActiveSessions() {
        return sessionRepository.findAllByStatusAndExpectedEndAtBefore("ACTIVE", OffsetDateTime.now());
    }

    private Service requireService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + serviceId));
    }

    private BookingResponse toBookingResponse(Booking b, Service s) {
        OffsetDateTime effectiveStart = b.getScheduledAt().plusMinutes(PREP_BUFFER_MINUTES);
        OffsetDateTime projectedEnd = effectiveStart.plusMinutes(s.getDurationMinutes());
        return new BookingResponse(b.getId(), b.getClientNickname(), b.getLockerNumber(),
                b.getServiceId(), b.getReservationType(), b.getScheduledAt(),
                effectiveStart, projectedEnd, b.getStatus());
    }

    private SessionResponse toSessionResponse(Session s) {
        return new SessionResponse(s.getId(), s.getBookingId(),
                s.getPrimaryTherapistId(), s.getSecondaryTherapistId(),
                s.getRoomId(), s.getBedId(), s.isSpecificallyRequested(),
                s.isExtension(), s.getExtensionMinutes(),
                s.getStartedAt(), s.getExpectedEndAt(), s.getEndedAt(), s.getStatus());
    }
}
