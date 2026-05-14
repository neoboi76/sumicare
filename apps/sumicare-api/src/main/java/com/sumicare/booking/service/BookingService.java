package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.SessionResponse;
import com.sumicare.booking.dto.StartSessionRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.service.PosService;
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
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
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
    private final TreatmentSlipService treatmentSlipService;
    private final OrderRepository orderRepository;
    private final PosService posService;
    private final PosTransactionRepository transactionRepository;

    public BookingService(BookingRepository bookingRepository, SessionRepository sessionRepository,
                          ServiceRepository serviceRepository, TherapistRepository therapistRepository,
                          RoomRepository roomRepository, BedRepository bedRepository,
                          RoomOccupancyService occupancyService, DeckingService deckingService,
                          NotificationService notificationService,
                          TreatmentSlipRepository slipRepository,
                          TreatmentSlipService treatmentSlipService,
                          OrderRepository orderRepository,
                          PosService posService,
                          PosTransactionRepository transactionRepository) {
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
        this.treatmentSlipService = treatmentSlipService;
        this.orderRepository = orderRepository;
        this.posService = posService;
        this.transactionRepository = transactionRepository;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public BookingResponse createBooking(UUID organizationId, CreateBookingRequest request) {
        Service service = requireService(request.serviceId());

        if (request.clientNickname() == null || request.clientNickname().isBlank()) {
            throw new IllegalArgumentException("Client nickname is required");
        }

        if (request.scheduledAt() != null && !"WALK_IN".equals(request.reservationType())) {
            OffsetDateTime now = OffsetDateTime.now();
            if (request.scheduledAt().isBefore(now)) {
                throw new IllegalArgumentException("Cannot book a session in the past");
            }
        }

        boolean hasActive = bookingRepository.existsByOrganizationIdAndClientNicknameIgnoreCaseAndStatusIn(
                organizationId, request.clientNickname(), java.util.List.of("PENDING", "ACTIVE"));
        if (hasActive) {
            throw new IllegalStateException("Client already has an ongoing booking");
        }

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

        com.sumicare.cashier.domain.Order order = new com.sumicare.cashier.domain.Order();
        order.setOrganizationId(organizationId);
        order.setBookingId(booking.getId());
        order.setSubtotal(service.getPrice() == null ? java.math.BigDecimal.ZERO : service.getPrice());
        order.setTotal(service.getPrice() == null ? java.math.BigDecimal.ZERO : service.getPrice());
        order.setStatus("OPEN");
        order.setTransactorName(booking.getClientNickname());
        order.setRoomType("COMMON");
        order.setRoomTypeCharge(java.math.BigDecimal.ZERO);
        orderRepository.save(order);

        return toBookingResponse(booking, service);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse startSession(UUID organizationId, UUID bookingId, StartSessionRequest request) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Service service = requireService(booking.getServiceId());


        com.sumicare.cashier.domain.Order order = orderRepository.findByBookingId(bookingId).orElse(null);
        if (order != null && !"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be paid before starting a session. Current status: " + order.getStatus());
        }

        if (request.roomId() != null) {
            Room room = roomRepository.findById(request.roomId()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown room"));
            boolean vipAllowed = (order != null && "VIP".equalsIgnoreCase(order.getRoomType())) || service.isVip();
            if ("VIP".equalsIgnoreCase(room.getRoomType()) && !vipAllowed) {
                throw new IllegalStateException("VIP room can only be selected for VIP-package orders");
            }
            if (!"VIP".equalsIgnoreCase(room.getRoomType()) && request.bedId() != null) {
                String clientGender = booking.getClientGender();
                if (clientGender != null && !clientGender.isBlank()) {
                    List<Bed> roomBeds = bedRepository.findAllByRoomId(room.getId());
                    for (Bed bed : roomBeds) {
                        if (bed.getId().equals(request.bedId())) continue;
                        Map<Object, Object> occ = occupancyService.read(room.getId(), bed.getId());
                        Object status = occ.get("status");
                        if ("OCCUPIED".equals(status)) {
                            Object lock = occ.get("genderLock");
                            if (lock != null && !lock.toString().isEmpty() && !lock.toString().equals(clientGender)) {
                                throw new IllegalStateException("Gender conflict: room is occupied by " +
                                        ("M".equals(lock.toString()) ? "male" : "female") +
                                        " clients. Cannot place a " +
                                        ("M".equals(clientGender) ? "male" : "female") + " client.");
                            }
                        }
                    }
                }
            }
        }

        if (request.primaryTherapistId() != null) {
            boolean onCall = sessionRepository.existsByPrimaryTherapistIdAndStatus(
                    request.primaryTherapistId(), "ACTIVE");
            if (onCall) {
                throw new IllegalStateException("Primary therapist is currently on call and cannot be assigned to another session.");
            }
        }
        if (request.secondaryTherapistId() != null) {
            boolean onCall = sessionRepository.existsByPrimaryTherapistIdAndStatus(
                    request.secondaryTherapistId(), "ACTIVE")
                    || sessionRepository.existsBySecondaryTherapistIdAndStatus(
                    request.secondaryTherapistId(), "ACTIVE");
            if (onCall) {
                throw new IllegalStateException("Secondary therapist is currently on call and cannot be assigned to another session.");
            }
        }

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

        if (order != null) {
            List<PosTransaction> transactions = transactionRepository.findAllByOrderId(order.getId());
            for (PosTransaction tx : transactions) {
                if (tx.getSessionId() == null) {
                    tx.setSessionId(session.getId());
                    transactionRepository.save(tx);
                }
            }
        }

        if (request.roomId() != null && request.bedId() != null) {
            Therapist primary = request.primaryTherapistId() == null ? null
                    : therapistRepository.findById(request.primaryTherapistId()).orElse(null);
            String therapistNickname = primary == null ? "" : primary.getNickname();
            String genderLock = booking.getClientGender();
            occupancyService.occupy(organizationId, request.roomId(), request.bedId(),
                    booking.getClientNickname(), booking.getLockerNumber(),
                    therapistNickname, genderLock);
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

        TreatmentSlip slip = treatmentSlipService.generateForSession(organizationId, sessionId);
        slip.setEndTime(now);
        slipRepository.save(slip);

        orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
            order.setTreatmentSlipId(slip.getId());
            orderRepository.save(order);
        });

        if (session.getRoomId() != null && session.getBedId() != null) {
            notificationService.broadcastRoomUpdate(organizationId, session.getRoomId(), session.getBedId(),
                    Map.of("event", "SESSION_ENDED", "sessionId", session.getId()));
        }

        posService.recordCommissionsForSession(organizationId, session);

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
    public SessionResponse cancelSession(UUID organizationId, UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        if ("COMPLETED".equals(session.getStatus())) {
            throw new IllegalStateException("Cannot cancel a completed session");
        }

        session.setStatus("CANCELLED");
        if (session.getRoomId() != null && session.getBedId() != null) {
            occupancyService.release(organizationId, session.getRoomId(), session.getBedId());
            notificationService.broadcastRoomUpdate(organizationId, session.getRoomId(), session.getBedId(),
                    Map.of("event", "SESSION_CANCELLED", "sessionId", session.getId()));
        }

        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        booking.setStatus("PENDING");

        orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
            order.setStatus("OPEN");
            orderRepository.save(order);
            
            // Reverse any ledger entries if necessary, though posService.recordCommissionsForSession is not called yet,
            // we should make sure transactions pointing to this session are reversed or kept if paid.
            // But since session wasn't COMPLETED, commissions weren't created.
        });

        return toSessionResponse(session);
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
        UUID orderId = orderRepository.findByBookingId(b.getId())
                .map(com.sumicare.cashier.domain.Order::getId)
                .orElse(null);
        return new BookingResponse(b.getId(), b.getClientNickname(), b.getLockerNumber(),
                b.getServiceId(), b.getReservationType(), b.getScheduledAt(),
                effectiveStart, projectedEnd, b.getStatus(), orderId);
    }

    private SessionResponse toSessionResponse(Session s) {
        return new SessionResponse(s.getId(), s.getBookingId(),
                s.getPrimaryTherapistId(), s.getSecondaryTherapistId(),
                s.getRoomId(), s.getBedId(), s.isSpecificallyRequested(),
                s.isExtension(), s.getExtensionMinutes(),
                s.getStartedAt(), s.getExpectedEndAt(), s.getEndedAt(), s.getStatus());
    }
}
