package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.CreateBookingItemRequest;
import com.sumicare.booking.dto.PublicAttendeeRequest;
import com.sumicare.booking.dto.PublicPaymentResponse;
import com.sumicare.booking.dto.SessionResponse;
import com.sumicare.booking.dto.StartSessionRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.dto.PaymentDetailsRequest;
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
import com.sumicare.print.ReceiptPdfService;
import com.sumicare.print.TreatmentSlipPdfService;
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
    private final PosTransactionRepository transactionRepository;
    private final com.sumicare.cashier.repository.OrderItemAttendeeRepository attendeeRepository;
    private final com.sumicare.transaction.repository.CommissionRepository commissionRepository;
    private final com.sumicare.cashier.repository.OrderItemRepository orderItemRepository;
    private final com.sumicare.cashier.repository.PackageTierRepository packageTierRepository;
    private final PackageRepository packageRepository;
    private final com.sumicare.cashier.service.PackageService packageService;
    private final com.sumicare.voucher.service.VoucherService voucherService;
    private final ClientRepository clientRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final OrderService orderService;
    private final EmailService emailService;
    private final ReceiptPdfService receiptPdfService;
    private final TreatmentSlipPdfService treatmentSlipPdfService;
    private final com.sumicare.pos.service.PayMongoService payMongoService;
    private final LockerAssignmentService lockerAssignmentService;

    public BookingService(BookingRepository bookingRepository, SessionRepository sessionRepository,
                          ServiceRepository serviceRepository, TherapistRepository therapistRepository,
                          RoomRepository roomRepository, BedRepository bedRepository,
                          RoomOccupancyService occupancyService, DeckingService deckingService,
                          NotificationService notificationService,
                          TreatmentSlipRepository slipRepository,
                          TreatmentSlipService treatmentSlipService,
                          OrderRepository orderRepository,
                          PosTransactionRepository transactionRepository,
                          com.sumicare.cashier.repository.OrderItemAttendeeRepository attendeeRepository,
                          com.sumicare.transaction.repository.CommissionRepository commissionRepository,
                          com.sumicare.cashier.repository.OrderItemRepository orderItemRepository,
                          com.sumicare.cashier.repository.PackageTierRepository packageTierRepository,
                          PackageRepository packageRepository,
                          com.sumicare.cashier.service.PackageService packageService,
                          com.sumicare.voucher.service.VoucherService voucherService,
                          ClientRepository clientRepository,
                          TransactionLedgerRepository ledgerRepository,
                          @Lazy OrderService orderService,
                          EmailService emailService,
                          ReceiptPdfService receiptPdfService,
                          TreatmentSlipPdfService treatmentSlipPdfService,
                          com.sumicare.pos.service.PayMongoService payMongoService,
                          LockerAssignmentService lockerAssignmentService) {
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
        this.transactionRepository = transactionRepository;
        this.attendeeRepository = attendeeRepository;
        this.commissionRepository = commissionRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageTierRepository = packageTierRepository;
        this.packageRepository = packageRepository;
        this.packageService = packageService;
        this.voucherService = voucherService;
        this.clientRepository = clientRepository;
        this.ledgerRepository = ledgerRepository;
        this.orderService = orderService;
        this.emailService = emailService;
        this.receiptPdfService = receiptPdfService;
        this.treatmentSlipPdfService = treatmentSlipPdfService;
        this.payMongoService = payMongoService;
        this.lockerAssignmentService = lockerAssignmentService;
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
            Client existing = clientRepository.findByOrganizationIdAndEmailAndDeletedAtIsNull(organizationId, request.clientEmail()).orElse(null);
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
                if (!request.clientNickname().equalsIgnoreCase(existing.getNickname())) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT,
                            "That email is already registered to another nickname. Use a different email.");
                }
                if (request.nationality() != null && (existing.getNationality() == null || existing.getNationality().isBlank())) {
                    existing.setNationality(request.nationality());
                    clientRepository.save(existing);
                }
                resolvedClientId = existing.getId();
            }
        }

        if (request.items() != null && !request.items().isEmpty()) {
            return createMultiPackageBooking(organizationId, request, resolvedClientId, service);
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

        boolean publicReservation = !"WALK_IN".equalsIgnoreCase(request.reservationType());
        List<String> assignedLockers = new java.util.ArrayList<>();
        if (publicReservation) {
            java.util.Set<String> takenLockers = lockerAssignmentService.takenLockersForDay(organizationId, request.scheduledAt());
            for (PublicAttendeeRequest a : attendees) {
                String attendeeGender = a.clientGender() != null ? a.clientGender() : request.clientGender();
                assignedLockers.add(lockerAssignmentService.assign(attendeeGender, takenLockers));
            }
        }
        String resolvedBookingLocker = publicReservation
                ? (assignedLockers.isEmpty() ? null : assignedLockers.get(0))
                : request.lockerNumber();

        Booking booking = new Booking();
        booking.setOrganizationId(organizationId);
        booking.setClientId(resolvedClientId);
        booking.setClientNickname(request.clientNickname());
        booking.setClientEmail(request.clientEmail());
        booking.setLockerNumber(resolvedBookingLocker);
        booking.setServiceId(request.serviceId());
        booking.setReservationType(request.reservationType());
        booking.setScheduledAt(request.scheduledAt());
        booking.setPax(resolvedPax);
        booking.setClientGender(request.clientGender());
        booking.setNationality(request.nationality());
        booking.setRemarks(request.remarks());
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
        order.setNotes(request.remarks());
        order.setGroupBooking(resolvedPax > 1 || coupleOrVip);
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
            item.setRoomType(resolvedRoomType);
            item.setRoomTypeCharge(resolvedRoomCharge);
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
                validateVipMassageDuration(selectedPackage != null && selectedPackage.isRequiresVipRoom(), resolvedServiceId);
                com.sumicare.cashier.domain.OrderItemAttendee att = new com.sumicare.cashier.domain.OrderItemAttendee();
                att.setOrderItemId(item.getId());
                att.setOrderId(order.getId());
                att.setOrganizationId(organizationId);
                att.setServiceId(resolvedServiceId);
                att.setPackageTierId(resolvedTierId);
                att.setLockerNumber(publicReservation ? assignedLockers.get(pos)
                        : (a.lockerNumber() != null ? a.lockerNumber() : request.lockerNumber()));
                att.setClientGender(a.clientGender() != null ? a.clientGender() : request.clientGender());
                att.setPosition(pos++);
                attendeeRepository.save(att);
            }
        }

        return finalizeBooking(order, booking, service, selectedPackage, resolvedRoomType, orderTotal, request);
    }

    private void applyVoucher(Order order, Booking booking, String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) return;
        var voucher = voucherService.findValid(order.getOrganizationId(), voucherCode).orElse(null);
        if (voucher == null) return;
        BigDecimal base;
        if (voucher.getTargetPackageId() != null) {
            base = orderItemRepository.findAllByOrderIdOrderByPosition(order.getId()).stream()
                    .filter(it -> voucher.getTargetPackageId().equals(it.getPackageId()))
                    .map(it -> (it.getLineTotal() == null ? BigDecimal.ZERO : it.getLineTotal())
                            .add(it.getRoomTypeCharge() == null ? BigDecimal.ZERO : it.getRoomTypeCharge()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (base.signum() <= 0) return;
        } else {
            base = order.getSubtotal() == null ? BigDecimal.ZERO : order.getSubtotal();
        }
        BigDecimal discount = voucherService.computeDiscount(voucher, base);
        if (discount.signum() <= 0) return;
        order.setDiscount(discount);
        order.setTotal((order.getTotal() == null ? BigDecimal.ZERO : order.getTotal()).subtract(discount).max(BigDecimal.ZERO));
        order.setVoucherId(voucher.getId());
        orderRepository.save(order);
        voucherService.markRedeemed(voucher.getId(), booking.getClientId());
    }

    private BookingResponse finalizeBooking(Order order, Booking booking, Service service, Package selectedPackage,
                                            String resolvedRoomType, BigDecimal orderTotal, CreateBookingRequest request) {
        applyVoucher(order, booking, request.voucherCode());
        if (order.getTotal() != null) orderTotal = order.getTotal();
        boolean onlinePayment = "HARD".equalsIgnoreCase(request.reservationType())
                && com.sumicare.pos.service.PayMongoService.supports(request.paymentMethod());

        if ("HARD".equalsIgnoreCase(request.reservationType()) && !onlinePayment) {
            order.setOrNumber(orderService.nextOrNumber());
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
            orderRepository.save(order);

            orderService.recordPaymentInternal(order, null, null, "CASH", orderTotal, "hard_reservation");
            orderRepository.save(order);

            orderService.materialiseAttendeeSessions(order);
            orderRepository.save(order);
        }

        if (!onlinePayment && request.clientEmail() != null && !request.clientEmail().isBlank()) {
            sendBookingEmail(booking, order, resolvedRoomType, orderTotal, order.getOrNumber());
        }

        notificationService.broadcastBookingEvent(booking.getOrganizationId(), "BOOKING_CREATED", booking.getId(),
                booking.getClientNickname() == null ? "New booking" : "New booking from " + booking.getClientNickname());

        return toBookingResponse(booking, service);
    }

    private BookingResponse createMultiPackageBooking(UUID organizationId, CreateBookingRequest request,
                                                      UUID resolvedClientId, Service service) {
        boolean publicReservation = !"WALK_IN".equalsIgnoreCase(request.reservationType());
        List<CreateBookingItemRequest> items = request.items();

        int totalAttendees = 0;
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal roomChargeTotal = BigDecimal.ZERO;
        java.util.Set<String> roomTypes = new java.util.LinkedHashSet<>();
        Package firstPackage = null;
        List<Package> packages = new java.util.ArrayList<>();
        List<List<PublicAttendeeRequest>> normalisedAttendees = new java.util.ArrayList<>();
        List<String> itemRoomTypes = new java.util.ArrayList<>();
        List<BigDecimal> itemRoomCharges = new java.util.ArrayList<>();
        List<BigDecimal> itemLineTotals = new java.util.ArrayList<>();

        boolean anyCoupleOrVip = false;
        for (CreateBookingItemRequest ci : items) {
            if (ci.packageId() == null) {
                throw new IllegalArgumentException("Each booking item must reference a package");
            }
            Package pkg = packageRepository.findById(ci.packageId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown package"));
            boolean coupleOrVip = pkg.isCouple() || pkg.isRequiresVipRoom();
            if (coupleOrVip) anyCoupleOrVip = true;
            List<PublicAttendeeRequest> attendees = padAttendees(ci.attendees(), pkg, coupleOrVip, request.clientGender());

            String roomType = resolveRoomType(pkg, ci.roomType());
            BigDecimal roomCharge = resolveRoomCharge(roomType);
            BigDecimal lineTotal = computeLineTotal(ci, attendees, coupleOrVip);

            if (firstPackage == null) firstPackage = pkg;
            packages.add(pkg);
            normalisedAttendees.add(attendees);
            itemRoomTypes.add(roomType);
            itemRoomCharges.add(roomCharge);
            itemLineTotals.add(lineTotal);
            roomTypes.add(roomType);
            roomChargeTotal = roomChargeTotal.add(roomCharge);
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            totalAttendees += attendees.size();
        }

        BigDecimal orderTotal = itemsSubtotal.add(roomChargeTotal);
        String orderRoomType = roomTypes.size() == 1 ? roomTypes.iterator().next() : "MIXED";

        List<List<String>> assignedLockers = new java.util.ArrayList<>();
        if (publicReservation) {
            java.util.Set<String> takenLockers = lockerAssignmentService.takenLockersForDay(organizationId, request.scheduledAt());
            for (List<PublicAttendeeRequest> group : normalisedAttendees) {
                List<String> groupLockers = new java.util.ArrayList<>();
                for (PublicAttendeeRequest a : group) {
                    String attendeeGender = a.clientGender() != null ? a.clientGender() : request.clientGender();
                    groupLockers.add(lockerAssignmentService.assign(attendeeGender, takenLockers));
                }
                assignedLockers.add(groupLockers);
            }
        }
        String bookingLocker = request.lockerNumber();
        if (publicReservation) {
            bookingLocker = assignedLockers.stream().flatMap(List::stream).findFirst().orElse(null);
        }

        Booking booking = new Booking();
        booking.setOrganizationId(organizationId);
        booking.setClientId(resolvedClientId);
        booking.setClientNickname(request.clientNickname());
        booking.setClientEmail(request.clientEmail());
        booking.setLockerNumber(bookingLocker);
        booking.setServiceId(request.serviceId());
        booking.setReservationType(request.reservationType());
        booking.setScheduledAt(request.scheduledAt());
        booking.setPax(totalAttendees);
        booking.setClientGender(request.clientGender());
        booking.setNationality(request.nationality());
        booking.setRemarks(request.remarks());
        booking.setStatus("PENDING");
        bookingRepository.save(booking);

        Order order = new Order();
        order.setOrganizationId(organizationId);
        order.setBookingId(booking.getId());
        order.setSubtotal(orderTotal);
        order.setTotal(orderTotal);
        order.setStatus("OPEN");
        order.setTransactorName(booking.getClientNickname());
        order.setRoomType(orderRoomType);
        order.setRoomTypeCharge(roomChargeTotal);
        order.setNotes(request.remarks());
        order.setGroupBooking(totalAttendees > 1 || anyCoupleOrVip);
        orderRepository.save(order);

        int position = 0;
        for (int i = 0; i < items.size(); i++) {
            CreateBookingItemRequest ci = items.get(i);
            Package pkg = packages.get(i);
            boolean coupleOrVip = pkg.isCouple() || pkg.isRequiresVipRoom();
            List<PublicAttendeeRequest> attendees = normalisedAttendees.get(i);
            BigDecimal lineTotal = itemLineTotals.get(i);
            int quantity = coupleOrVip ? 1 : attendees.size();

            com.sumicare.cashier.domain.OrderItem item = new com.sumicare.cashier.domain.OrderItem();
            item.setOrderId(order.getId());
            item.setOrganizationId(organizationId);
            item.setPackageId(pkg.getId());
            item.setQuantity(quantity);
            item.setUnitPrice(quantity > 0
                    ? lineTotal.divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP)
                    : lineTotal);
            item.setLineTotal(lineTotal);
            item.setRoomType(itemRoomTypes.get(i));
            item.setRoomTypeCharge(itemRoomCharges.get(i));
            item.setPosition(position++);
            orderItemRepository.save(item);

            Long sharedTierId = coupleOrVip && !attendees.isEmpty() ? resolveTierId(attendees.get(0), ci) : null;
            int attPos = 0;
            for (PublicAttendeeRequest a : attendees) {
                Long tierId = coupleOrVip ? sharedTierId : resolveTierId(a, ci);
                Long resolvedServiceId = request.serviceId();
                if (tierId != null) {
                    com.sumicare.cashier.domain.PackageTier t = packageTierRepository.findById(tierId).orElse(null);
                    if (t != null && t.getServiceId() != null) {
                        resolvedServiceId = t.getServiceId();
                    }
                }
                validateVipMassageDuration(pkg.isRequiresVipRoom(), resolvedServiceId);
                com.sumicare.cashier.domain.OrderItemAttendee att = new com.sumicare.cashier.domain.OrderItemAttendee();
                att.setOrderItemId(item.getId());
                att.setOrderId(order.getId());
                att.setOrganizationId(organizationId);
                att.setServiceId(resolvedServiceId);
                att.setPackageTierId(tierId);
                att.setLockerNumber(publicReservation ? assignedLockers.get(i).get(attPos) : a.lockerNumber());
                att.setClientGender(a.clientGender() != null ? a.clientGender() : request.clientGender());
                att.setPosition(attPos++);
                attendeeRepository.save(att);
            }
        }

        return finalizeBooking(order, booking, service, firstPackage, orderRoomType, orderTotal, request);
    }

    private List<PublicAttendeeRequest> padAttendees(List<PublicAttendeeRequest> attendees, Package pkg,
                                                     boolean coupleOrVip, String fallbackGender) {
        List<PublicAttendeeRequest> resolved = attendees == null || attendees.isEmpty()
                ? new java.util.ArrayList<>(List.of(new PublicAttendeeRequest(null, null, fallbackGender)))
                : new java.util.ArrayList<>(attendees);
        if (coupleOrVip) {
            int requiredPax = Math.max(2, pkg.getDefaultPax());
            PublicAttendeeRequest seed = resolved.get(0);
            while (resolved.size() < requiredPax) {
                resolved.add(new PublicAttendeeRequest(seed.packageTierId(), null, seed.clientGender()));
            }
        }
        return resolved;
    }

    private Long resolveTierId(PublicAttendeeRequest attendee, CreateBookingItemRequest item) {
        return attendee.packageTierId() != null ? attendee.packageTierId() : item.packageTierId();
    }

    private BigDecimal computeLineTotal(CreateBookingItemRequest item, List<PublicAttendeeRequest> attendees,
                                        boolean coupleOrVip) {
        if (coupleOrVip) {
            Long tierId = !attendees.isEmpty() ? resolveTierId(attendees.get(0), item) : item.packageTierId();
            return tierWeekdayPrice(tierId);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PublicAttendeeRequest a : attendees) {
            sum = sum.add(tierWeekdayPrice(resolveTierId(a, item)));
        }
        return sum;
    }

    private BigDecimal tierWeekdayPrice(Long tierId) {
        if (tierId == null) return BigDecimal.ZERO;
        return packageTierRepository.findById(tierId)
                .map(com.sumicare.cashier.domain.PackageTier::getWeekdayPrice)
                .orElse(BigDecimal.ZERO);
    }

    private String resolveRoomType(Package pkg, String requestedRoomType) {
        if (pkg.isRequiresVipRoom()) return "VIP";
        if ("PRIVATE".equalsIgnoreCase(requestedRoomType)) return "PRIVATE";
        return "COMMON";
    }

    private BigDecimal resolveRoomCharge(String roomType) {
        return "PRIVATE".equalsIgnoreCase(roomType) ? new BigDecimal("500") : BigDecimal.ZERO;
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public PublicPaymentResponse initiatePublicPayment(UUID organizationId, UUID orderId,
                                                       String paymentMethod, PaymentDetailsRequest details) {
        Order order = requirePublicOrder(organizationId, orderId);
        if ("PAID".equals(order.getStatus())) {
            return paymentResponse("succeeded", null, null, order);
        }
        if (order.getTotal() == null || order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("This reservation has no payable total");
        }
        if (!com.sumicare.pos.service.PayMongoService.supports(paymentMethod)) {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        }
        com.sumicare.pos.service.PayMongoService.ChargeResult result = payMongoService.initiate(
                order, order.getTotal(), paymentMethod, null, details, "/book");
        if ("succeeded".equalsIgnoreCase(result.status())) {
            settlePublicPayment(order, result.intentId(), paymentMethod);
            return paymentResponse("succeeded", result.intentId(), null, order);
        }
        return paymentResponse(result.status(), result.intentId(), result.nextActionUrl(), order);
    }

    @PreAuthorize("permitAll()")
    @Transactional
    public PublicPaymentResponse confirmPublicPayment(UUID organizationId, UUID orderId,
                                                      String intentId, String paymentMethod) {
        Order order = requirePublicOrder(organizationId, orderId);
        if ("PAID".equals(order.getStatus())) {
            return paymentResponse("succeeded", intentId, null, order);
        }
        String status = payMongoService.confirm(intentId);
        if (!"succeeded".equalsIgnoreCase(status) && !"processing".equalsIgnoreCase(status)) {
            throw new IllegalStateException("PayMongo payment was not authorized (status: " + status + ")");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required to confirm this payment.");
        }
        settlePublicPayment(order, intentId, paymentMethod);
        return paymentResponse("succeeded", intentId, null, order);
    }

    private PublicPaymentResponse paymentResponse(String status, String intentId, String redirectUrl, Order order) {
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        String nickname = booking == null ? null : booking.getClientNickname();
        String reservationType = booking == null ? null : booking.getReservationType();
        String scheduledAt = booking == null || booking.getScheduledAt() == null
                ? null : booking.getScheduledAt().toString();
        String serviceName = booking == null || booking.getServiceId() == null ? null
                : serviceRepository.findById(booking.getServiceId()).map(Service::getName).orElse(null);
        Package pkg = resolveOrderPackage(order);
        String packageName = pkg == null ? null : pkg.getName();
        return new PublicPaymentResponse(status, intentId, redirectUrl, order.getOrNumber(),
                nickname, packageName, serviceName, scheduledAt, reservationType);
    }

    private Order requirePublicOrder(UUID organizationId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order"));
        if (!order.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Order not in organization");
        }
        return order;
    }

    private String settlePublicPayment(Order order, String intentId, String paymentMethod) {
        if (order.getOrNumber() == null || order.getOrNumber().isBlank()) {
            order.setOrNumber(orderService.nextOrNumber());
            orderRepository.save(order);
        }
        orderService.settleGatewayPayment(order, null, intentId, paymentMethod, order.getTotal());
        return order.getOrNumber();
    }

    public void resendBookingConfirmation(Order order) {
        if (order == null || order.getBookingId() == null || !"PAID".equals(order.getStatus())) {
            return;
        }
        Booking booking = bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        String recipient = resolveClientEmail(booking);
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        sendBookingEmail(booking, order, order.getRoomType(), order.getTotal(), order.getOrNumber());
    }

    private Package resolveOrderPackage(Order order) {
        return orderItemRepository.findAllByOrderIdOrderByPosition(order.getId()).stream()
                .filter(it -> it.getPackageId() != null)
                .findFirst()
                .flatMap(it -> packageRepository.findById(it.getPackageId()))
                .orElse(null);
    }

    private void sendBookingEmail(Booking booking, Order order, String roomType, BigDecimal total, String orNumber) {
        try {
            String recipient = resolveClientEmail(booking);
            if (recipient == null || recipient.isBlank()) return;
            OffsetDateTime effectiveStart = booking.getScheduledAt().plusMinutes(PREP_BUFFER_MINUTES);
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a");
            List<EmailService.PackageLine> lines = new java.util.ArrayList<>();
            for (com.sumicare.cashier.domain.OrderItem item : orderItemRepository.findAllByOrderIdOrderByPosition(order.getId())) {
                Package pkg = item.getPackageId() == null ? null
                        : packageRepository.findById(item.getPackageId()).orElse(null);
                String name = pkg != null ? pkg.getName() : "Service";
                java.util.LinkedHashSet<String> massages = new java.util.LinkedHashSet<>();
                for (com.sumicare.cashier.domain.OrderItemAttendee att : attendeeRepository.findAllByOrderItemIdOrderByPosition(item.getId())) {
                    if (att.getServiceId() != null) {
                        serviceRepository.findById(att.getServiceId()).ifPresent(s -> massages.add(s.getName()));
                    }
                }
                List<String> inclusions = pkg != null ? packageService.deriveInclusions(pkg) : List.of();
                lines.add(new EmailService.PackageLine(name, String.join(", ", massages), inclusions));
            }
            emailService.sendBookingConfirmationEmail(
                    recipient,
                    booking.getClientNickname(),
                    new EmailService.BookingEmailPayload(
                            booking.getId().toString(),
                            orNumber,
                            lines,
                            booking.getReservationType(),
                            booking.getScheduledAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Manila")).format(fmt),
                            effectiveStart.atZoneSameInstant(java.time.ZoneId.of("Asia/Manila")).format(fmt),
                            roomType,
                            describePaymentMethods(order),
                            total.toPlainString()));
        } catch (Exception ex) {
            log.warn("Could not send booking confirmation email to {}: {}", booking.getClientEmail(), ex.getMessage());
        }
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
        if (!organizationId.equals(order.getOrganizationId())) {
            throw new org.springframework.security.access.AccessDeniedException("Attendee belongs to another organization.");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be paid before starting a session. Current status: " + order.getStatus());
        }
        String lockerNumber = attendee.getLockerNumber();
        if (lockerNumber == null || lockerNumber.isBlank()) {
            throw new IllegalStateException("Assign a locker number before starting this guest's session.");
        }
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        Service service = attendee.getServiceId() != null ? requireService(attendee.getServiceId()) : null;
        String clientGender = attendee.getClientGender();

        if (clientGender != null && !clientGender.isBlank()) {
            for (Session active : sessionRepository.findAllByOrganizationIdAndStatus(organizationId, "ACTIVE")) {
                if (active.getAttendeeId() == null || active.getAttendeeId().equals(attendeeId)) continue;
                var other = attendeeRepository.findById(active.getAttendeeId()).orElse(null);
                if (other == null) continue;
                if (lockerNumber.equalsIgnoreCase(other.getLockerNumber())
                        && clientGender.equalsIgnoreCase(other.getClientGender())) {
                    throw new IllegalStateException("Locker " + clientGender.toUpperCase() + lockerNumber
                            + " is already in use by another active session.");
                }
            }
        }

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

        String itemRoomType = orderItemRepository.findById(attendee.getOrderItemId())
                .map(com.sumicare.cashier.domain.OrderItem::getRoomType)
                .orElse(order.getRoomType());
        if (request.roomId() != null) {
            Room room = roomRepository.findById(request.roomId()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown room"));
            boolean vipAllowed = "VIP".equalsIgnoreCase(itemRoomType);
            if ("VIP".equalsIgnoreCase(room.getRoomType()) && !vipAllowed) {
                throw new IllegalStateException("VIP room can only be selected for VIP packages");
            }
        boolean isPrivateOrVip = "PRIVATE".equalsIgnoreCase(room.getRoomType())
                || "VIP".equalsIgnoreCase(room.getRoomType());
        UUID placingItemId = attendee.getOrderItemId();
        if (!isPrivateOrVip && request.bedId() != null
                && clientGender != null && !clientGender.isBlank()) {
                List<Bed> roomBeds = bedRepository.findAllByRoomId(room.getId());
                for (Bed bed : roomBeds) {
                    if (bed.getId().equals(request.bedId())) continue;
                    Map<Object, Object> occ = occupancyService.read(room.getId(), bed.getId());
                    if ("OCCUPIED".equals(occ.get("status"))) {
                        Object owner = occ.get("ownerItemId");
                        boolean samePackage = placingItemId != null && owner != null
                                && placingItemId.toString().equals(owner.toString());
                        if (samePackage) continue;
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
            boolean onCall = sessionRepository.existsByOrganizationIdAndPrimaryTherapistIdAndStatus(
                    organizationId, request.primaryTherapistId(), "ACTIVE")
                    || sessionRepository.existsByOrganizationIdAndSecondaryTherapistIdAndStatus(
                    organizationId, request.primaryTherapistId(), "ACTIVE");
            if (onCall) {
                throw new IllegalStateException("Primary therapist is currently on call and cannot be assigned to another session.");
            }
        }
        if (request.secondaryTherapistId() != null) {
            boolean onCall = sessionRepository.existsByOrganizationIdAndPrimaryTherapistIdAndStatus(
                    organizationId, request.secondaryTherapistId(), "ACTIVE")
                    || sessionRepository.existsByOrganizationIdAndSecondaryTherapistIdAndStatus(
                    organizationId, request.secondaryTherapistId(), "ACTIVE");
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

        if (booking != null && !isMultiAttendeeOrder(order)) {
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
                    nickname, attendee.getLockerNumber(), therapistNickname, clientGender, attendee.getOrderItemId());
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

        if (isMultiAttendeeOrder(order) && booking != null && booking.getStatus() != null && !"ACTIVE".equals(booking.getStatus())) {
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
        return endSessionAt(organizationId, sessionId, OffsetDateTime.now());
    }

    @Transactional
    public SessionResponse endSessionAt(UUID organizationId, UUID sessionId, OffsetDateTime endTime) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        if (!"ACTIVE".equals(session.getStatus())) {
            return toSessionResponse(session);
        }
        OffsetDateTime now = endTime != null ? endTime : OffsetDateTime.now();
        session.setEndedAt(now);
        session.setStatus("COMPLETED");
        sessionRepository.save(session);

        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();

        boolean isGroupSession = orderRepository.findByBookingId(booking.getId())
                .map(this::isMultiAttendeeOrder)
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
        if (!"VOIDED".equals(slip.getStatus())) {
            slip.setEndTime(now);
            slipRepository.save(slip);
        }

        orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
            if (!isMultiAttendeeOrder(order)) {
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

        orderService.recordCommissionsForSession(organizationId, session);

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

        maybeSendCompletionEmail(booking);

        return toSessionResponse(session);
    }

    private String resolveClientEmail(Booking booking) {
        if (booking == null) return null;
        if (booking.getClientEmail() != null && !booking.getClientEmail().isBlank()) {
            return booking.getClientEmail();
        }
        if (booking.getClientId() != null) {
            return clientRepository.findById(booking.getClientId())
                    .map(Client::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
        }
        return null;
    }

    @Transactional
    public void sendOrderConfirmationEmail(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) return;
        String email = resolveClientEmail(booking);
        if (email == null || email.isBlank()) return;
        Order order = orderRepository.findByBookingId(bookingId).orElse(null);
        if (order == null) return;
        sendBookingEmail(booking, order, order.getRoomType(),
                order.getTotal() == null ? BigDecimal.ZERO : order.getTotal(), order.getOrNumber());
    }

    private void maybeSendCompletionEmail(Booking booking) {
        if (booking == null || !"COMPLETED".equals(booking.getStatus())) return;
        String email = resolveClientEmail(booking);
        if (email == null || email.isBlank()) return;
        Order order = orderRepository.findByBookingId(booking.getId()).orElse(null);
        if (order == null || order.getCompletionEmailSentAt() != null) return;
        BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
        BigDecimal paid = order.getAmountPaid() == null ? BigDecimal.ZERO : order.getAmountPaid();
        if (paid.compareTo(total) < 0) return;
        try {
            byte[] receipt = receiptPdfService.renderReceipt(order.getId());
            List<EmailService.EmailAttachment> slips = new java.util.ArrayList<>();
            int idx = 1;
            for (TreatmentSlip slip : slipRepository.findAllByBookingId(booking.getId())) {
                if ("VOIDED".equals(slip.getStatus())) continue;
                byte[] pdf = treatmentSlipPdfService.renderSlip(slip.getId());
                String label = slip.getTsn() != null && !slip.getTsn().isBlank() ? slip.getTsn() : String.valueOf(idx);
                slips.add(new EmailService.EmailAttachment("treatment-slip-" + label + ".pdf", pdf));
                idx++;
            }
            OffsetDateTime scheduled = booking.getScheduledAt();
            String paymentMethod = describePaymentMethods(order);
            List<String> slipLines = buildSlipSummaryLines(booking.getId());
            EmailService.CompletionEmailPayload payload = new EmailService.CompletionEmailPayload(
                    booking.getId().toString(),
                    order.getOrNumber(),
                    buildAvailedLines(order),
                    formatManila(scheduled),
                    formatManila(scheduled == null ? null : scheduled.plusMinutes(PREP_BUFFER_MINUTES)),
                    total.toPlainString(),
                    paymentMethod,
                    slipLines);
            emailService.sendCompletionEmail(email, booking.getClientNickname(), payload, receipt, slips);
            order.setCompletionEmailSentAt(OffsetDateTime.now());
            orderRepository.save(order);
        } catch (Exception ex) {
            log.warn("Could not send completion email for booking {}: {}", booking.getId(), ex.getMessage());
        }
    }

    private String describePaymentMethods(Order order) {
        var txs = transactionRepository.findAllByOrderId(order.getId());
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        for (var tx : txs) {
            String m = tx.getPaymentMethod();
            if (m == null || m.isBlank()) continue;
            labels.add(humanisePaymentMethod(m));
        }
        return String.join(", ", labels);
    }

    private String humanisePaymentMethod(String m) {
        if (m == null) return "";
        return switch (m.toUpperCase()) {
            case "CASH" -> "Cash";
            case "GCASH" -> "GCash";
            case "CREDIT" -> "Credit card";
            case "DEBIT" -> "Debit card";
            case "CARD" -> "Card";
            default -> m;
        };
    }

    private List<String> buildSlipSummaryLines(UUID bookingId) {
        List<String> lines = new java.util.ArrayList<>();
        int idx = 1;
        for (TreatmentSlip slip : slipRepository.findAllByBookingId(bookingId)) {
            if ("VOIDED".equals(slip.getStatus())) continue;
            String tsn = slip.getTsn() == null ? String.valueOf(idx) : slip.getTsn();
            String guest = slip.getClientNickname() == null ? "Guest" : slip.getClientNickname();
            String locker = slip.getLockerNumber() == null ? "" : slip.getLockerNumber().replaceFirst("^[MFmf]", "");
            String service = slip.getServiceName() == null ? "" : slip.getServiceName();
            StringBuilder line = new StringBuilder("TS #").append(tsn).append(" &middot; ").append(guest);
            if (!service.isBlank()) line.append(" &middot; ").append(service);
            if (!locker.isBlank()) line.append(" &middot; Locker ").append(locker);
            lines.add(line.toString());
            idx++;
        }
        return lines;
    }

    private List<String> buildAvailedLines(Order order) {
        List<String> lines = new java.util.ArrayList<>();
        for (com.sumicare.cashier.domain.OrderItem item : orderItemRepository.findAllByOrderIdOrderByPosition(order.getId())) {
            String packageName = item.getPackageId() == null ? "Service"
                    : packageRepository.findById(item.getPackageId()).map(Package::getName).orElse("Package");
            java.util.LinkedHashSet<String> services = new java.util.LinkedHashSet<>();
            for (com.sumicare.cashier.domain.OrderItemAttendee att : attendeeRepository.findAllByOrderItemIdOrderByPosition(item.getId())) {
                if (att.getServiceId() != null) {
                    serviceRepository.findById(att.getServiceId())
                            .ifPresent(s -> services.add(s.getName()));
                }
            }
            String roomLabel = "VIP".equalsIgnoreCase(item.getRoomType()) ? "VIP room"
                    : "PRIVATE".equalsIgnoreCase(item.getRoomType()) ? "Private room" : "Common room";
            StringBuilder line = new StringBuilder(packageName);
            if (!services.isEmpty()) {
                line.append(" — ").append(String.join(", ", services));
            }
            line.append(" (").append(roomLabel).append(")");
            lines.add(line.toString());
        }
        return lines;
    }

    private String formatManila(OffsetDateTime time) {
        if (time == null) return "";
        return time.atZoneSameInstant(java.time.ZoneId.of("Asia/Manila"))
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a"));
    }

    @Transactional
    public void autoEndSession(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if ("ACTIVE".equals(session.getStatus())) {
                OffsetDateTime endTime = session.getExpectedEndAt() != null
                        ? session.getExpectedEndAt() : OffsetDateTime.now();
                endSessionAt(session.getOrganizationId(), sessionId, endTime);
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

        slipRepository.findBySessionId(session.getId()).ifPresent(slip -> {
            if (!"VOIDED".equals(slip.getStatus())) {
                slip.setStatus("VOIDED");
                slipRepository.save(slip);
            }
        });

        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        booking.setStatus("PENDING");

        orderRepository.findByBookingId(booking.getId()).ifPresent(order -> {
            order.setStatus("OPEN");
            orderRepository.save(order);
        });

        return toSessionResponse(session);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public SessionResponse extendSession(UUID sessionId, int additionalMinutes) {
        Session session = sessionRepository.findByIdForUpdate(sessionId).orElseThrow();
        if (additionalMinutes != 60) {
            throw new IllegalArgumentException("Extensions are billed per hour; pass additionalMinutes=60.");
        }
        if (session.isExtension()) {
            throw new IllegalStateException("This session has already been extended once. No further extensions are allowed.");
        }
        if (isFixedRateSession(session)) {
            throw new IllegalStateException("This service has a fixed duration and cannot be extended.");
        }
        Order parentOrder = session.getBookingId() == null ? null
                : orderRepository.findByBookingId(session.getBookingId()).orElse(null);
        boolean vip = isVipPackageSession(session);

        session.setExtension(true);
        session.setExtensionMinutes(session.getExtensionMinutes() + additionalMinutes);
        if (session.getExpectedEndAt() != null) {
            session.setExpectedEndAt(session.getExpectedEndAt().plusMinutes(additionalMinutes));
        }

        int halfHours = (int) Math.ceil(additionalMinutes / 30.0);
        BigDecimal commissionAmount = BigDecimal.valueOf(60L * halfHours);

        if (!vip && parentOrder != null) {
            BigDecimal rate = parentOrder.isWeekend() ? new BigDecimal("900") : new BigDecimal("800");
            BigDecimal newSubtotal = (parentOrder.getSubtotal() == null ? BigDecimal.ZERO : parentOrder.getSubtotal()).add(rate);
            BigDecimal newTotal = (parentOrder.getTotal() == null ? BigDecimal.ZERO : parentOrder.getTotal()).add(rate);
            BigDecimal newExtension = (parentOrder.getExtensionAmount() == null ? BigDecimal.ZERO : parentOrder.getExtensionAmount()).add(rate);
            parentOrder.setSubtotal(newSubtotal);
            parentOrder.setTotal(newTotal);
            parentOrder.setExtensionAmount(newExtension);
            parentOrder.setExtensionMinutes(parentOrder.getExtensionMinutes() + additionalMinutes);
            BigDecimal amountPaid = parentOrder.getAmountPaid() == null ? BigDecimal.ZERO : parentOrder.getAmountPaid();
            if (amountPaid.compareTo(newTotal) < 0 && "PAID".equals(parentOrder.getStatus())) {
                parentOrder.setStatus("OPEN");
            }
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

        slipRepository.findBySessionId(session.getId()).ifPresent(slip -> {
            slip.setExtensionMinutes(session.getExtensionMinutes());
            if (vip) {
                slip.setJacuzziMinutes(0);
                slip.setMassageMinutes(120);
            }
            slipRepository.save(slip);
        });

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
        return bookingRepository.findAllByOrganizationIdAndEffectiveDateBetween(organizationId, dayStart, dayEnd)
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
                OffsetDateTime endTime = s.getExpectedEndAt() != null ? s.getExpectedEndAt() : OffsetDateTime.now();
                endSessionAt(organizationId, s.getId(), endTime);
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

    private boolean isFixedRateSession(Session session) {
        Long serviceId = null;
        if (session.getAttendeeId() != null) {
            serviceId = attendeeRepository.findById(session.getAttendeeId())
                    .map(com.sumicare.cashier.domain.OrderItemAttendee::getServiceId)
                    .orElse(null);
        }
        if (serviceId == null && session.getBookingId() != null) {
            serviceId = bookingRepository.findById(session.getBookingId())
                    .map(Booking::getServiceId)
                    .orElse(null);
        }
        if (serviceId == null) {
            return false;
        }
        return serviceRepository.findById(serviceId).map(Service::isFixedRate).orElse(false);
    }

    private boolean isVipPackageSession(Session session) {
        if (session.getAttendeeId() == null) {
            return false;
        }
        return attendeeRepository.findById(session.getAttendeeId())
                .map(com.sumicare.cashier.domain.OrderItemAttendee::getOrderItemId)
                .flatMap(orderItemRepository::findById)
                .map(com.sumicare.cashier.domain.OrderItem::getPackageId)
                .flatMap(packageRepository::findById)
                .map(Package::isRequiresVipRoom)
                .orElse(false);
    }

    private boolean isMultiAttendeeOrder(Order order) {
        return attendeeRepository.findAllByOrderIdOrderByPosition(order.getId()).size() > 1;
    }

    private void validateVipMassageDuration(boolean vipPackage, Long serviceId) {
        if (!vipPackage || serviceId == null) {
            return;
        }
        int duration = serviceRepository.findById(serviceId).map(Service::getDurationMinutes).orElse(0);
        if (duration > 120) {
            throw new IllegalArgumentException("VIP massages may not exceed 120 minutes");
        }
    }

    private BookingResponse toBookingResponse(Booking b, Service s) {
        OffsetDateTime effectiveStart = b.getScheduledAt().plusMinutes(PREP_BUFFER_MINUTES);
        int maxDuration = s.getDurationMinutes();
        com.sumicare.cashier.domain.Order order = orderRepository.findByBookingId(b.getId()).orElse(null);
        UUID orderId = order == null ? null : order.getId();
        if (order != null) {
            List<com.sumicare.cashier.domain.OrderItemAttendee> atts =
                    attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
            int sessionMax = 0;
            OffsetDateTime latestExpectedEnd = null;
            for (com.sumicare.cashier.domain.OrderItemAttendee a : atts) {
                Long sid = a.getServiceId() != null ? a.getServiceId() : b.getServiceId();
                if (sid != null) {
                    Service svc = serviceRepository.findById(sid).orElse(null);
                    if (svc != null) sessionMax = Math.max(sessionMax, svc.getDurationMinutes());
                }
                if (a.getSessionId() != null) {
                    Session sess = sessionRepository.findById(a.getSessionId()).orElse(null);
                    if (sess != null && sess.getExpectedEndAt() != null) {
                        if (latestExpectedEnd == null || sess.getExpectedEndAt().isAfter(latestExpectedEnd)) {
                            latestExpectedEnd = sess.getExpectedEndAt();
                        }
                    }
                }
            }
            if (sessionMax > 0) maxDuration = Math.max(maxDuration, sessionMax);
            OffsetDateTime projectedEnd = latestExpectedEnd != null
                    ? latestExpectedEnd
                    : effectiveStart.plusMinutes(maxDuration);
            return new BookingResponse(b.getId(), b.getClientNickname(), b.getClientEmail(), b.getLockerNumber(),
                    b.getServiceId(), b.getReservationType(), effectiveStart,
                    projectedEnd, b.getStatus(), orderId, b.getNationality(), b.getRemarks());
        }
        OffsetDateTime projectedEnd = effectiveStart.plusMinutes(maxDuration);
        return new BookingResponse(b.getId(), b.getClientNickname(), b.getClientEmail(), b.getLockerNumber(),
                b.getServiceId(), b.getReservationType(), effectiveStart,
                projectedEnd, b.getStatus(), orderId, b.getNationality(), b.getRemarks());
    }

    private SessionResponse toSessionResponse(Session s) {
        return new SessionResponse(s.getId(), s.getBookingId(),
                s.getPrimaryTherapistId(), s.getSecondaryTherapistId(),
                s.getRoomId(), s.getBedId(), s.isSpecificallyRequested(),
                s.isExtension(), s.getExtensionMinutes(),
                s.getStartedAt(), s.getExpectedEndAt(), s.getEndedAt(), s.getStatus());
    }
}
