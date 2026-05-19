package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.PublicAttendeeRequest;
import com.sumicare.booking.dto.SessionResponse;
import com.sumicare.booking.dto.StartSessionRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.cashier.service.OrderService;
import com.sumicare.client.domain.Client;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
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
import com.sumicare.transaction.domain.Commission;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
import com.sumicare.auth.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

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
    private final com.sumicare.cashier.repository.OrderItemAttendeeRepository attendeeRepository;
    private final com.sumicare.transaction.repository.CommissionRepository commissionRepository;
    private final com.sumicare.cashier.repository.OrderItemRepository orderItemRepository;
    private final com.sumicare.cashier.repository.PackageTierRepository packageTierRepository;
    private final PackageRepository packageRepository;
    private final ClientRepository clientRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final OrderService orderService;
    private final EmailService emailService;

    public BookingService(BookingRepository bookingRepository, SessionRepository sessionRepository,
                          ServiceRepository serviceRepository, TherapistRepository therapistRepository,
                          RoomRepository roomRepository, BedRepository bedRepository,
                          RoomOccupancyService occupancyService, DeckingService deckingService,
                          NotificationService notificationService,
                          TreatmentSlipRepository slipRepository,
                          TreatmentSlipService treatmentSlipService,
                          OrderRepository orderRepository,
                          PosService posService,
                          PosTransactionRepository transactionRepository,
                          com.sumicare.cashier.repository.OrderItemAttendeeRepository attendeeRepository,
                          com.sumicare.transaction.repository.CommissionRepository commissionRepository,
                          com.sumicare.cashier.repository.OrderItemRepository orderItemRepository,
                          com.sumicare.cashier.repository.PackageTierRepository packageTierRepository,
                          PackageRepository packageRepository,
                          ClientRepository clientRepository,
                          TransactionLedgerRepository ledgerRepository,
                          @Lazy OrderService orderService,
                          EmailService emailService) {
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
        this.attendeeRepository = attendeeRepository;
        this.commissionRepository = commissionRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageTierRepository = packageTierRepository;
        this.packageRepository = packageRepository;
        this.clientRepository = clientRepository;
        this.ledgerRepository = ledgerRepository;
        this.orderService = orderService;
        this.emailService = emailService;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public BookingResponse createBooking(UUID organizationId, CreateBookingRequest request) {
        Service service = requireService(request.serviceId());

        if (request.clientNickname() == null || request.clientNickname().isBlank()) {
            throw new IllegalArgumentException("Client nickname is required");
        }
        if (!"WALK_IN".equalsIgnoreCase(request.reservationType())
                && (request.clientEmail() == null || request.clientEmail().isBlank())) {
            throw new IllegalArgumentException("Client email is required for online bookings");
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

        UUID resolvedClientId = request.clientId();
        if (resolvedClientId == null && request.clientEmail() != null && !request.clientEmail().isBlank()) {
            Client existing = clientRepository.findByOrganizationIdAndEmail(organizationId, request.clientEmail()).orElse(null);
            if (existing == null) {
                Client created = new Client();
                created.setOrganizationId(organizationId);
                created.setNickname(request.clientNickname());
                created.setEmail(request.clientEmail());
                created.setGender(request.clientGender());
                created.setNationality(request.nationality());
                created.setConsentGiven(true);
                Client saved = clientRepository.save(created);
                resolvedClientId = saved.getId();
            } else {
                if (request.nationality() != null && (existing.getNationality() == null || existing.getNationality().isBlank())) {
                    existing.setNationality(request.nationality());
                    clientRepository.save(existing);
                }
                resolvedClientId = existing.getId();
            }
        }

        Package selectedPackage = request.packageId() == null ? null
                : packageRepository.findById(request.packageId()).orElse(null);
        com.sumicare.cashier.domain.PackageTier defaultTier = request.packageTierId() == null ? null
                : packageTierRepository.findById(request.packageTierId()).orElse(null);
        BigDecimal defaultTierPrice = defaultTier != null
                ? defaultTier.getWeekdayPrice()
                : (service.getPrice() == null ? BigDecimal.ZERO : service.getPrice());

        boolean coupleOrVip = selectedPackage != null
                && (selectedPackage.isCouple() || selectedPackage.isRequiresVipRoom());

        List<PublicAttendeeRequest> requestedAttendees = request.attendees();
        if (requestedAttendees == null || requestedAttendees.isEmpty()) {
            requestedAttendees = List.of(new PublicAttendeeRequest(
                    request.packageTierId(), request.lockerNumber(), request.clientGender()));
        }
        if (coupleOrVip && selectedPackage != null) {
            int requiredPax = Math.max(2, selectedPackage.getDefaultPax());
            if (requestedAttendees.size() < requiredPax) {
                List<PublicAttendeeRequest> padded = new java.util.ArrayList<>(requestedAttendees);
                PublicAttendeeRequest seed = requestedAttendees.get(0);
                while (padded.size() < requiredPax) {
                    padded.add(new PublicAttendeeRequest(seed.packageTierId(), null, seed.clientGender()));
                }
                requestedAttendees = padded;
            }
        }
        final List<PublicAttendeeRequest> attendees = requestedAttendees;

        String resolvedRoomType;
        BigDecimal resolvedRoomCharge;
        if (selectedPackage != null && selectedPackage.isRequiresVipRoom()) {
            resolvedRoomType = "VIP";
            resolvedRoomCharge = BigDecimal.ZERO;
        } else if ("PRIVATE".equalsIgnoreCase(request.roomType())) {
            resolvedRoomType = "PRIVATE";
            resolvedRoomCharge = new BigDecimal("500");
        } else {
            resolvedRoomType = "COMMON";
            resolvedRoomCharge = BigDecimal.ZERO;
        }

        BigDecimal lineTotal;
        if (coupleOrVip) {
            lineTotal = defaultTierPrice;
        } else {
            BigDecimal sum = BigDecimal.ZERO;
            for (PublicAttendeeRequest a : attendees) {
                com.sumicare.cashier.domain.PackageTier t = a.packageTierId() != null
                        ? packageTierRepository.findById(a.packageTierId()).orElse(defaultTier)
                        : defaultTier;
                BigDecimal p = t != null
                        ? t.getWeekdayPrice()
                        : defaultTierPrice;
                sum = sum.add(p == null ? BigDecimal.ZERO : p);
            }
            lineTotal = sum;
        }
        BigDecimal orderTotal = lineTotal.add(resolvedRoomCharge);

        int resolvedPax = attendees.size();

        Booking booking = new Booking();
        booking.setOrganizationId(organizationId);
        booking.setClientId(resolvedClientId);
        booking.setClientNickname(request.clientNickname());
        booking.setClientEmail(request.clientEmail());
        booking.setLockerNumber(request.lockerNumber());
        booking.setServiceId(request.serviceId());
        booking.setReservationType(request.reservationType());
        booking.setScheduledAt(request.scheduledAt());
        booking.setPax(resolvedPax);
        booking.setClientGender(request.clientGender());
        booking.setNationality(request.nationality());
        booking.setStatus("PENDING");
        bookingRepository.save(booking);

        com.sumicare.cashier.domain.Order order = new com.sumicare.cashier.domain.Order();
        order.setOrganizationId(organizationId);
        order.setBookingId(booking.getId());
        order.setSubtotal(orderTotal);
        order.setTotal(orderTotal);
        order.setStatus("OPEN");
        order.setTransactorName(booking.getClientNickname());
        order.setRoomType(resolvedRoomType);
        order.setRoomTypeCharge(resolvedRoomCharge);
        orderRepository.save(order);

        if (request.packageId() != null) {
            com.sumicare.cashier.domain.OrderItem item = new com.sumicare.cashier.domain.OrderItem();
            item.setOrderId(order.getId());
            item.setOrganizationId(organizationId);
            item.setPackageId(request.packageId());
            item.setQuantity(coupleOrVip ? 1 : resolvedPax);
            item.setUnitPrice(coupleOrVip ? lineTotal
                    : (resolvedPax > 0
                        ? lineTotal.divide(BigDecimal.valueOf(resolvedPax), 2, java.math.RoundingMode.HALF_UP)
                        : lineTotal));
            item.setLineTotal(lineTotal);
            item.setPosition(0);
            orderItemRepository.save(item);

            int pos = 0;
            for (PublicAttendeeRequest a : attendees) {
                Long resolvedTierId = a.packageTierId() != null ? a.packageTierId() : request.packageTierId();
                Long resolvedServiceId = request.serviceId();
                if (resolvedTierId != null) {
                    com.sumicare.cashier.domain.PackageTier t = packageTierRepository.findById(resolvedTierId).orElse(null);
                    if (t != null && t.getServiceId() != null) {
                        resolvedServiceId = t.getServiceId();
                    }
                }
                com.sumicare.cashier.domain.OrderItemAttendee att = new com.sumicare.cashier.domain.OrderItemAttendee();
                att.setOrderItemId(item.getId());
                att.setOrderId(order.getId());
                att.setOrganizationId(organizationId);
                att.setServiceId(resolvedServiceId);
                att.setPackageTierId(resolvedTierId);
                att.setLockerNumber(a.lockerNumber() != null ? a.lockerNumber() : request.lockerNumber());
                att.setClientGender(a.clientGender() != null ? a.clientGender() : request.clientGender());
                att.setPosition(pos++);
                attendeeRepository.save(att);
            }
        }

        if ("HARD".equalsIgnoreCase(request.reservationType())) {
            order.setOrNumber(orderService.nextOrNumber());
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
            orderRepository.save(order);

            orderService.recordPaymentInternal(order, null, null, "CASH", orderTotal, "hard_reservation");
            orderRepository.save(order);

            orderService.materialiseAttendeeSessions(order);
            orderRepository.save(order);
        }

        if (request.clientEmail() != null && !request.clientEmail().isBlank()) {
            try {
                OffsetDateTime effectiveStart = booking.getScheduledAt().plusMinutes(PREP_BUFFER_MINUTES);
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a");
                emailService.sendBookingConfirmationEmail(
                        request.clientEmail(),
                        request.clientNickname(),
                        new EmailService.BookingEmailPayload(
                                booking.getId().toString(),
                                selectedPackage == null ? null : selectedPackage.getName(),
                                service.getName(),
                                request.reservationType(),
                                booking.getScheduledAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Manila")).format(fmt),
                                effectiveStart.atZoneSameInstant(java.time.ZoneId.of("Asia/Manila")).format(fmt),
                                resolvedRoomType,
                                orderTotal.toPlainString()));
            } catch (Exception ex) {
                log.warn("Could not send booking confirmation email to {}: {}", request.clientEmail(), ex.getMessage());
            }
        }

        return toBookingResponse(booking, service);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse startSession(UUID organizationId, UUID bookingId, StartSessionRequest request) {
        com.sumicare.cashier.domain.Order order = orderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException("No order found for this booking"));
        List<com.sumicare.cashier.domain.OrderItemAttendee> attendees =
                attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
        if (attendees.isEmpty()) {
            throw new IllegalStateException("This order has no attendees to start a session for");
        }
        return startAttendeeSession(organizationId, attendees.get(0).getId(), request);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse startAttendeeSession(UUID organizationId, UUID attendeeId, StartSessionRequest request) {
        com.sumicare.cashier.domain.OrderItemAttendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown attendee"));
        com.sumicare.cashier.domain.Order order = orderRepository.findById(attendee.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Order not found for attendee"));
        if (!"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be paid before starting a session. Current status: " + order.getStatus());
        }
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        Service service = attendee.getServiceId() != null ? requireService(attendee.getServiceId()) : null;
        String clientGender = attendee.getClientGender();

        Session session = attendee.getSessionId() != null
                ? sessionRepository.findById(attendee.getSessionId()).orElse(null)
                : null;
        if (session == null) {
            session = new Session();
            session.setOrganizationId(organizationId);
            session.setBookingId(order.getBookingId());
            session.setAttendeeId(attendee.getId());
            session.setStatus("ACTIVE");
            session = sessionRepository.save(session);
            attendee.setSessionId(session.getId());
        }
        if ("COMPLETED".equals(session.getStatus())) {
            throw new IllegalStateException("This session has already ended.");
        }

        if (request.roomId() != null) {
            Room room = roomRepository.findById(request.roomId()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown room"));
            boolean vipAllowed = "VIP".equalsIgnoreCase(order.getRoomType()) || (service != null && service.isVip());
            if ("VIP".equalsIgnoreCase(room.getRoomType()) && !vipAllowed) {
                throw new IllegalStateException("VIP room can only be selected for VIP-package orders");
            }
        boolean isPrivateOrVip = "PRIVATE".equalsIgnoreCase(room.getRoomType())
                || "VIP".equalsIgnoreCase(room.getRoomType());
        boolean isCouplePkg = orderItemRepository.findById(attendee.getOrderItemId())
                .map(item -> packageRepository.findById(item.getPackageId())
                        .map(Package::isCouple).orElse(false))
                .orElse(false);
        if (!isPrivateOrVip && !isCouplePkg && request.bedId() != null
                && clientGender != null && !clientGender.isBlank()) {
                List<Bed> roomBeds = bedRepository.findAllByRoomId(room.getId());
                for (Bed bed : roomBeds) {
                    if (bed.getId().equals(request.bedId())) continue;
                    Map<Object, Object> occ = occupancyService.read(room.getId(), bed.getId());
                    if ("OCCUPIED".equals(occ.get("status"))) {
                        Object lock = occ.get("genderLock");
                        if (lock != null && !lock.toString().isEmpty() && !lock.toString().equals(clientGender)) {
                            throw new IllegalStateException("Gender conflict: this common room is occupied by " +
                                    ("M".equals(lock.toString()) ? "male" : "female") +
                                    " clients. Cannot place a " +
                                    ("M".equals(clientGender) ? "male" : "female") + " client here.");
                        }
                    }
                }
            }
        }

        if (request.primaryTherapistId() != null && deckingService.isSkipped(organizationId, request.primaryTherapistId())) {
            throw new IllegalStateException("Primary therapist is on break; cancel their break before assigning.");
        }
        if (request.secondaryTherapistId() != null && deckingService.isSkipped(organizationId, request.secondaryTherapistId())) {
            throw new IllegalStateException("Secondary therapist is on break; cancel their break before assigning.");
        }
        if (request.primaryTherapistId() != null) {
            boolean onCall = sessionRepository.existsByPrimaryTherapistIdAndStatus(
                    request.primaryTherapistId(), "ACTIVE")
                    || sessionRepository.existsBySecondaryTherapistIdAndStatus(
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

        if (service != null && service.isRequiresTwoTherapists() && request.secondaryTherapistId() == null) {
            throw new IllegalArgumentException("This service requires two therapists; pick a secondary.");
        }

        session.setPrimaryTherapistId(request.primaryTherapistId());
        session.setSecondaryTherapistId(request.secondaryTherapistId());
        session.setRoomId(request.roomId());
        session.setBedId(request.bedId());
        session.setSpecificallyRequested(request.specificallyRequested());

        OffsetDateTime now = OffsetDateTime.now();
        session.setStartedAt(now);
        if (service != null) {
            session.setExpectedEndAt(now.plusMinutes(service.getDurationMinutes()));
        }
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
        attendeeRepository.save(attendee);

        if (booking != null && !order.isGroupBooking()) {
            booking.setActualStartAt(now);
            booking.setStatus("ACTIVE");
        }

        List<PosTransaction> transactions = transactionRepository.findAllByOrderId(order.getId());
        for (PosTransaction tx : transactions) {
            if (tx.getSessionId() == null) {
                tx.setSessionId(session.getId());
                transactionRepository.save(tx);
            }
        }

        if (request.roomId() != null && request.bedId() != null) {
            Therapist primary = request.primaryTherapistId() == null ? null
                    : therapistRepository.findById(request.primaryTherapistId()).orElse(null);
            String therapistNickname = primary == null ? "" : primary.getNickname();
            String nickname = booking != null ? booking.getClientNickname()
                    : (order.getTransactorName() == null ? "" : order.getTransactorName());
            occupancyService.occupy(organizationId, request.roomId(), request.bedId(),
                    nickname, attendee.getLockerNumber(), therapistNickname, clientGender);
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

        TreatmentSlip slip = treatmentSlipService.generateForSession(organizationId, session.getId());
        if (slip != null) {
            attendee.setTreatmentSlipId(slip.getId());
            attendeeRepository.save(attendee);
        }

        if (order.isGroupBooking() && booking != null && booking.getStatus() != null && !"ACTIVE".equals(booking.getStatus())) {
            List<com.sumicare.cashier.domain.OrderItemAttendee> allAtts = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
            boolean allActive = allAtts.stream()
                    .filter(a -> a.getSessionId() != null)
                    .allMatch(a -> sessionRepository.findById(a.getSessionId())
                            .map(s -> "ACTIVE".equals(s.getStatus()))
                            .orElse(false));
            if (!allAtts.isEmpty() && allAtts.stream().allMatch(a -> a.getSessionId() != null) && allActive) {
                booking.setActualStartAt(now);
                booking.setStatus("ACTIVE");
                bookingRepository.save(booking);
            }
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
        sessionRepository.save(session);

        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();

        boolean isGroupSession = orderRepository.findByBookingId(booking.getId())
                .map(Order::isGroupBooking)
                .orElse(false);
        if (!isGroupSession) {
            booking.setActualEndAt(now);
            booking.setStatus("COMPLETED");
        } else {
            orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
                List<com.sumicare.cashier.domain.OrderItemAttendee> allAtts =
                        attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
                boolean allDone = allAtts.stream()
                        .filter(a -> a.getSessionId() != null)
                        .allMatch(a -> sessionRepository.findById(a.getSessionId())
                                .map(s -> "COMPLETED".equals(s.getStatus()))
                                .orElse(false));
                if (!allAtts.isEmpty() && allAtts.stream().allMatch(a -> a.getSessionId() != null) && allDone) {
                    booking.setActualEndAt(now);
                    booking.setStatus("COMPLETED");
                }
            });
        }

        TreatmentSlip slip = treatmentSlipService.generateForSession(organizationId, sessionId);
        slip.setEndTime(now);
        slipRepository.save(slip);

        orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
            if (!order.isGroupBooking()) {
                order.setTreatmentSlipId(slip.getId());
            }
            orderRepository.save(order);
        });

        if (session.getAttendeeId() != null) {
            attendeeRepository.findById(session.getAttendeeId()).ifPresent(att -> {
                att.setTreatmentSlipId(slip.getId());
                attendeeRepository.save(att);
            });
        }

        posService.recordCommissionsForSession(organizationId, session);

        UUID roomId = session.getRoomId();
        UUID bedId = session.getBedId();
        if (roomId != null && bedId != null) {
            try {
                occupancyService.release(organizationId, roomId, bedId);
            } catch (Exception e) {
                log.warn("Redis bed release failed for room {} bed {} — will be healed by reconciler: {}", roomId, bedId, e.getMessage());
            }
            notificationService.broadcastRoomUpdate(organizationId, roomId, bedId,
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
        if (additionalMinutes != 60) {
            throw new IllegalArgumentException("Extensions are billed per hour; pass additionalMinutes=60.");
        }
        if (session.isExtension()) {
            throw new IllegalStateException("This session has already been extended once. No further extensions are allowed.");
        }
        Order parentOrder = session.getBookingId() == null ? null
                : orderRepository.findByBookingId(session.getBookingId()).orElse(null);
        if (parentOrder != null) {
            boolean vip = orderItemRepository.findAllByOrderIdOrderByPosition(parentOrder.getId()).stream()
                    .anyMatch(it -> it.getPackageId() != null
                            && packageRepository.findById(it.getPackageId())
                                    .map(Package::isRequiresVipRoom).orElse(false));
            if (vip) {
                throw new IllegalStateException("VIP packages cannot be extended.");
            }
        }

        session.setExtension(true);
        session.setExtensionMinutes(session.getExtensionMinutes() + additionalMinutes);
        if (session.getExpectedEndAt() != null) {
            session.setExpectedEndAt(session.getExpectedEndAt().plusMinutes(additionalMinutes));
        }

        BigDecimal rate = (parentOrder != null && parentOrder.isWeekend())
                ? new BigDecimal("900") : new BigDecimal("800");
        BigDecimal commissionAmount = new BigDecimal("120");

        if (parentOrder != null) {
            BigDecimal newSubtotal = (parentOrder.getSubtotal() == null ? BigDecimal.ZERO : parentOrder.getSubtotal()).add(rate);
            BigDecimal newTotal = (parentOrder.getTotal() == null ? BigDecimal.ZERO : parentOrder.getTotal()).add(rate);
            parentOrder.setSubtotal(newSubtotal);
            parentOrder.setTotal(newTotal);
            orderRepository.save(parentOrder);
        }

        if (session.getPrimaryTherapistId() != null) {
            Commission commission = new Commission();
            commission.setOrganizationId(session.getOrganizationId());
            commission.setSessionId(session.getId());
            commission.setTherapistId(session.getPrimaryTherapistId());
            commission.setAmount(commissionAmount);
            commission.setExtension(true);
            commission.setServiceType("EXTENSION");
            commission.setCreatedAt(OffsetDateTime.now());
            commissionRepository.save(commission);
        }

        sessionRepository.save(session);
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

    @Transactional
    public int autoEndExpiredSessions(UUID organizationId) {
        List<Session> expired = sessionRepository
                .findAllByOrganizationIdAndStatusAndExpectedEndAtBefore(organizationId, "ACTIVE", OffsetDateTime.now());
        int ended = 0;
        for (Session s : expired) {
            try {
                endSession(organizationId, s.getId());
                ended++;
            } catch (Exception ex) {
                log.warn("autoEndExpiredSessions failed to end session {}: {}", s.getId(), ex.getMessage());
            }
        }
        return ended;
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
        return new BookingResponse(b.getId(), b.getClientNickname(), b.getClientEmail(), b.getLockerNumber(),
                b.getServiceId(), b.getReservationType(), effectiveStart,
                projectedEnd, b.getStatus(), orderId, b.getNationality());
    }

    private SessionResponse toSessionResponse(Session s) {
        return new SessionResponse(s.getId(), s.getBookingId(),
                s.getPrimaryTherapistId(), s.getSecondaryTherapistId(),
                s.getRoomId(), s.getBedId(), s.isSpecificallyRequested(),
                s.isExtension(), s.getExtensionMinutes(),
                s.getStartedAt(), s.getExpectedEndAt(), s.getEndedAt(), s.getStatus());
    }
}
