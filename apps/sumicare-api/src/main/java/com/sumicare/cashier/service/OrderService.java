package com.sumicare.cashier.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.booking.service.BookingService;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.OrderItem;
import com.sumicare.cashier.domain.OrderItemAttendee;
import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.dto.CreateOrderItemAttendeeRequest;
import com.sumicare.cashier.dto.CreateOrderItemRequest;
import com.sumicare.cashier.dto.CreateOrderRequest;
import com.sumicare.cashier.dto.OrderItemAttendeeResponse;
import com.sumicare.cashier.dto.OrderItemResponse;
import com.sumicare.cashier.dto.OrderResponse;
import com.sumicare.cashier.dto.RecordPaymentRequest;
import com.sumicare.cashier.repository.OrderItemAttendeeRepository;
import com.sumicare.cashier.repository.OrderItemRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import com.sumicare.room.service.RoomOccupancyService;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class OrderService {

    private static final Map<String, BigDecimal> ROOM_SURCHARGE = Map.of(
            "COMMON", BigDecimal.ZERO,
            "PRIVATE", new BigDecimal("500"),
            "VIP", BigDecimal.ZERO
    );

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final ServiceRepository serviceRepository;
    private final BookingService bookingService;
    private final TreatmentSlipService slipService;
    private final TreatmentSlipRepository slipRepository;
    private final PosTransactionRepository transactionRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final RoomOccupancyService occupancyService;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemAttendeeRepository attendeeRepository;
    private final PackageRepository packageRepository;
    private final com.sumicare.voucher.service.VoucherService voucherService;
    private final com.sumicare.transaction.repository.CommissionRepository commissionRepository;
    private final com.sumicare.pos.service.PayMongoService payMongoService;
    private final com.sumicare.notification.service.NotificationService notificationService;
    private final com.sumicare.common.util.IdSequenceService idSequenceService;

    public OrderService(OrderRepository orderRepository,
                        BookingRepository bookingRepository,
                        SessionRepository sessionRepository,
                        ServiceRepository serviceRepository,
                        BookingService bookingService,
                        TreatmentSlipService slipService,
                        TreatmentSlipRepository slipRepository,
                        PosTransactionRepository transactionRepository,
                        TransactionLedgerRepository ledgerRepository,
                        RoomOccupancyService occupancyService,
                        UserRepository userRepository,
                        OrderItemRepository orderItemRepository,
                        OrderItemAttendeeRepository attendeeRepository,
                        PackageRepository packageRepository,
                        com.sumicare.voucher.service.VoucherService voucherService,
                        com.sumicare.transaction.repository.CommissionRepository commissionRepository,
                        com.sumicare.pos.service.PayMongoService payMongoService,
                        com.sumicare.notification.service.NotificationService notificationService,
                        com.sumicare.common.util.IdSequenceService idSequenceService) {
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.bookingService = bookingService;
        this.slipService = slipService;
        this.slipRepository = slipRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.occupancyService = occupancyService;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.attendeeRepository = attendeeRepository;
        this.packageRepository = packageRepository;
        this.voucherService = voucherService;
        this.commissionRepository = commissionRepository;
        this.payMongoService = payMongoService;
        this.notificationService = notificationService;
        this.idSequenceService = idSequenceService;
    }

    @Transactional
    public void recordCommissionsForSession(UUID organizationId, Session session) {
        if (session.getPrimaryTherapistId() == null) return;
        if (commissionRepository.existsBySessionIdAndTherapistIdAndExtensionFalse(session.getId(), session.getPrimaryTherapistId())) return;
        Booking booking = bookingRepository.findById(session.getBookingId()).orElse(null);
        if (booking == null) return;
        Long serviceIdToResolve = booking.getServiceId();
        if (session.getAttendeeId() != null) {
            OrderItemAttendee attendee = attendeeRepository.findById(session.getAttendeeId()).orElse(null);
            if (attendee != null && attendee.getServiceId() != null) {
                serviceIdToResolve = attendee.getServiceId();
            }
        }
        var service = serviceIdToResolve == null ? null : serviceRepository.findById(serviceIdToResolve).orElse(null);
        BigDecimal base = service == null ? BigDecimal.ZERO : service.getCommissionAmount();
        boolean tandem = service != null && service.isRequiresTwoTherapists();
        Long svcId = service == null ? null : service.getId();
        String svcType = service == null ? null : service.getCategory();
        BigDecimal primaryShare = tandem
                ? base.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP)
                : base;
        com.sumicare.transaction.domain.Commission primaryComm = new com.sumicare.transaction.domain.Commission();
        primaryComm.setOrganizationId(organizationId);
        primaryComm.setSessionId(session.getId());
        primaryComm.setTherapistId(session.getPrimaryTherapistId());
        primaryComm.setAmount(primaryShare);
        primaryComm.setServiceId(svcId);
        primaryComm.setServiceType(svcType);
        primaryComm.setSpecificallyRequested(session.isSpecificallyRequested());
        commissionRepository.save(primaryComm);

        if (session.getSecondaryTherapistId() != null && tandem) {
            com.sumicare.transaction.domain.Commission secondaryComm = new com.sumicare.transaction.domain.Commission();
            secondaryComm.setOrganizationId(organizationId);
            secondaryComm.setSessionId(session.getId());
            secondaryComm.setTherapistId(session.getSecondaryTherapistId());
            secondaryComm.setAmount(primaryShare);
            secondaryComm.setServiceId(svcId);
            secondaryComm.setServiceType(svcType);
            secondaryComm.setSpecificallyRequested(false);
            commissionRepository.save(secondaryComm);
        }
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse create(UUID organizationId, UUID cashierUserId, CreateOrderRequest request) {
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        if (!hasItems && (request.serviceIds() == null || request.serviceIds().isEmpty())) {
            throw new IllegalArgumentException("Order must include at least one item or service");
        }

        String firstServiceName = null;
        Long firstServiceId = null;
        int totalAttendees = 0;
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal roomChargeTotal = BigDecimal.ZERO;
        boolean anyCoupleOrVip = false;
        Map<Long, Package> packageCache = new HashMap<>();
        java.util.List<String> itemRoomTypes = new java.util.ArrayList<>();
        java.util.List<BigDecimal> itemRoomCharges = new java.util.ArrayList<>();

        if (hasItems) {
            for (CreateOrderItemRequest ci : request.items()) {
                if (ci.packageId() == null) {
                    throw new IllegalArgumentException("Each order item must reference a package");
                }
                if (ci.attendees() == null || ci.attendees().isEmpty()) {
                    throw new IllegalArgumentException("Each order item must have at least one attendee");
                }
                totalAttendees += ci.attendees().size();
                BigDecimal lineTotal = ci.lineTotal() != null ? ci.lineTotal()
                        : (ci.unitPrice() != null
                            ? ci.unitPrice().multiply(BigDecimal.valueOf(ci.quantity() == null ? 1 : ci.quantity()))
                            : BigDecimal.ZERO);
                itemsSubtotal = itemsSubtotal.add(lineTotal);
                if (firstServiceId == null) {
                    for (CreateOrderItemAttendeeRequest a : ci.attendees()) {
                        if (a.serviceId() != null) { firstServiceId = a.serviceId(); break; }
                    }
                }
                Package pkg = packageCache.computeIfAbsent(ci.packageId(),
                        pid -> packageRepository.findById(pid).orElse(null));
                if (pkg != null && (pkg.isCouple() || pkg.isRequiresVipRoom())) anyCoupleOrVip = true;
                ItemRoom room = resolveItemRoom(pkg, ci.roomType());
                itemRoomTypes.add(room.roomType());
                itemRoomCharges.add(room.charge());
                roomChargeTotal = roomChargeTotal.add(room.charge());
            }
        } else {
            firstServiceId = request.serviceIds().get(0);
            totalAttendees = 1;
        }

        if (firstServiceId == null) {
            firstServiceId = request.serviceIds() != null && !request.serviceIds().isEmpty()
                    ? request.serviceIds().get(0)
                    : null;
        }

        if (firstServiceId != null) {
            var svc = serviceRepository.findById(firstServiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown service id"));
            firstServiceName = svc.getName();
        }

        String roomType = representativeRoomType(itemRoomTypes);
        BigDecimal roomCharge = roomChargeTotal;

        BigDecimal subtotalFromRequest = request.subtotal() != null
                ? request.subtotal()
                : itemsSubtotal.add(roomChargeTotal);
        BigDecimal attendeeDiscountTotal = BigDecimal.ZERO;
        if (hasItems) {
            for (CreateOrderItemRequest ci : request.items()) {
                for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                    if (ar.discount() != null) {
                        attendeeDiscountTotal = attendeeDiscountTotal.add(ar.discount());
                    }
                }
            }
        }
        BigDecimal requestDiscount = request.discount() != null ? request.discount() : BigDecimal.ZERO;
        BigDecimal discount = requestDiscount.add(attendeeDiscountTotal);
        BigDecimal tax = request.tax() != null ? request.tax() : BigDecimal.ZERO;
        BigDecimal total = request.total() != null
                ? request.total()
                : subtotalFromRequest.subtract(discount).add(tax).max(BigDecimal.ZERO);

        String nickname = request.clientNickname() != null ? request.clientNickname() : request.transactorName();
        if (nickname == null || nickname.isBlank()) nickname = "Walk-in";

        var bookingRequest = new CreateBookingRequest(
                request.clientId(),
                nickname,
                null,
                request.lockerNumber(),
                firstServiceId,
                "WALK_IN",
                OffsetDateTime.now(),
                request.pax() == null ? Math.max(1, totalAttendees) : request.pax(),
                request.clientGender(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        var bookingResponse = bookingService.createBooking(organizationId, bookingRequest);
        Booking booking = bookingRepository.findById(bookingResponse.id()).orElseThrow();

        Order order = orderRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            Order o = new Order();
            o.setOrganizationId(organizationId);
            o.setBookingId(booking.getId());
            return o;
        });
        order.setCashierUserId(cashierUserId);
        if (request.orNumber() != null && !request.orNumber().isBlank()) {
            order.setOrNumber(request.orNumber());
        } else if (order.getOrNumber() == null || order.getOrNumber().isBlank()) {
            order.setOrNumber(generateOrNumber());
        }
        order.setReferenceNumber(request.referenceNumber());
        order.setNotes(request.notes());
        order.setSubtotal(subtotalFromRequest);
        order.setDiscount(discount);
        order.setTax(tax);
        order.setTotal(total);
        order.setAmountPaid(BigDecimal.ZERO);
        order.setStatus("OPEN");
        order.setTransactorName(request.transactorName() != null ? request.transactorName() : nickname);
        order.setGroupBooking(request.items() != null && request.items().size() > 1);
        order.setWeekend(Boolean.TRUE.equals(request.weekend()));
        order.setRoomType(roomType);
        order.setRoomTypeCharge(roomCharge);
        order.setVoucherId(request.voucherId());
        order.setLastEditedByUserId(cashierUserId);
        orderRepository.save(order);

        if (request.voucherId() != null) {
            voucherService.markRedeemed(request.voucherId(), request.clientId());
        }

        if (hasItems) {
            AtomicInteger itemPos = new AtomicInteger(0);
            int roomIdx = 0;
            for (CreateOrderItemRequest ci : request.items()) {
                Package pkg = packageCache.computeIfAbsent(ci.packageId(),
                        pid -> packageRepository.findById(pid).orElse(null));
                boolean coupleOrVip = pkg != null && (pkg.isCouple() || pkg.isRequiresVipRoom());
                int derivedQuantity = coupleOrVip ? 1 : ci.attendees().size();
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setOrganizationId(organizationId);
                item.setPackageId(ci.packageId());
                item.setQuantity(derivedQuantity);
                item.setUnitPrice(ci.unitPrice() != null ? ci.unitPrice() : BigDecimal.ZERO);
                BigDecimal lt = ci.lineTotal() != null ? ci.lineTotal()
                        : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                item.setLineTotal(lt);
                item.setRoomType(itemRoomTypes.get(roomIdx));
                item.setRoomTypeCharge(itemRoomCharges.get(roomIdx));
                roomIdx++;
                item.setPosition(ci.position() != null ? ci.position() : itemPos.getAndIncrement());
                orderItemRepository.save(item);

                Long sharedTierId = null;
                Long sharedServiceId = null;
                if (coupleOrVip && !ci.attendees().isEmpty()) {
                    CreateOrderItemAttendeeRequest first = ci.attendees().get(0);
                    sharedTierId = first.packageTierId();
                    sharedServiceId = first.serviceId();
                }

                AtomicInteger attPos = new AtomicInteger(0);
                for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                    OrderItemAttendee att = new OrderItemAttendee();
                    att.setOrderItemId(item.getId());
                    att.setOrderId(order.getId());
                    att.setOrganizationId(organizationId);
                    Long resolvedServiceId = coupleOrVip ? sharedServiceId : ar.serviceId();
                    validateVipMassageDuration(pkg, resolvedServiceId);
                    att.setServiceId(resolvedServiceId);
                    att.setPackageTierId(coupleOrVip ? sharedTierId : ar.packageTierId());
                    att.setLockerNumber(ar.lockerNumber());
                    att.setClientGender(ar.clientGender());
                    att.setPosition(ar.position() != null ? ar.position() : attPos.getAndIncrement());
                    att.setDiscount(ar.discount() != null ? ar.discount() : BigDecimal.ZERO);
                    att.setProvidedTsn(ar.providedTsn());
                    attendeeRepository.save(att);
                }
            }
        }

        if (request.initialPayment() != null && total.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payAmount = request.initialPayment().amount();
            if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero");
            }
            if (payAmount.compareTo(total) > 0) {
                throw new IllegalArgumentException("Payment amount exceeds order total");
            }
            String initialReference = request.initialPayment().referenceNumber();
            if (com.sumicare.pos.service.PayMongoService.supports(request.initialPayment().paymentMethod())) {
                com.sumicare.pos.service.PayMongoService.ChargeResult result = payMongoService.charge(
                        order, payAmount, request.initialPayment().paymentMethod(),
                        initialReference, request.initialPayment().paymentDetails());
                initialReference = result.intentId();
            }
            recordPaymentInternal(order, null, cashierUserId, request.initialPayment().paymentMethod(),
                    payAmount, initialReference);
            if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
                order.setStatus("PAID");
                order.setFinishedAt(OffsetDateTime.now());
                materialiseAttendeeSessions(order);
            }
        } else if (total.compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus("PAID");
            order.setAmountPaid(BigDecimal.ZERO);
            order.setFinishedAt(OffsetDateTime.now());
            materialiseAttendeeSessions(order);
            PosTransaction tx = new PosTransaction();
            tx.setOrganizationId(order.getOrganizationId());
            tx.setOrderId(order.getId());
            tx.setReceiptNumber(generateReceiptNumber());
            tx.setSubtotal(BigDecimal.ZERO);
            tx.setDiscount(BigDecimal.ZERO);
            tx.setTotal(BigDecimal.ZERO);
            tx.setPaymentMethod("NONE");
            tx.setProcessedAt(OffsetDateTime.now());
            tx.setStatus("COMPLETED");
            tx = transactionRepository.save(tx);
            TransactionLedgerEntry entry = new TransactionLedgerEntry();
            entry.setOrganizationId(order.getOrganizationId());
            entry.setTransactionId(tx.getId());
            entry.setEntryType("FULLY_DISCOUNTED");
            entry.setAmount(BigDecimal.ZERO);
            entry.setPaymentMethod("NONE");
            entry.setStatus("COMPLETED");
            entry.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"reason\":\"fully_discounted\"}");
            ledgerRepository.save(entry);
        }

        if (request.clientId() != null) {
            bookingService.sendOrderConfirmationEmail(booking.getId());
        }

        notificationService.broadcastOrderEvent(order.getOrganizationId(), "ORDER_CREATED", order.getId(),
                order.getOrNumber() != null ? "New order " + order.getOrNumber() : "New order");

        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse update(UUID organizationId, UUID orderId, UUID actorUserId, CreateOrderRequest request) {
        Order order = requireOrder(organizationId, orderId);
        if (!"OPEN".equals(order.getStatus())) {
            throw new IllegalStateException("Re-open the order before editing");
        }
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        if (!hasItems) {
            throw new IllegalArgumentException("Order must include at least one item");
        }

        Long firstServiceId = null;
        int totalAttendees = 0;
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal roomChargeTotal = BigDecimal.ZERO;
        boolean anyCoupleOrVip = false;
        Map<Long, Package> packageCache = new HashMap<>();
        java.util.List<String> itemRoomTypes = new java.util.ArrayList<>();
        java.util.List<BigDecimal> itemRoomCharges = new java.util.ArrayList<>();
        for (CreateOrderItemRequest ci : request.items()) {
            if (ci.packageId() == null) {
                throw new IllegalArgumentException("Each order item must reference a package");
            }
            if (ci.attendees() == null || ci.attendees().isEmpty()) {
                throw new IllegalArgumentException("Each order item must have at least one attendee");
            }
            totalAttendees += ci.attendees().size();
            BigDecimal lineTotal = ci.lineTotal() != null ? ci.lineTotal()
                    : (ci.unitPrice() != null
                        ? ci.unitPrice().multiply(BigDecimal.valueOf(ci.quantity() == null ? 1 : ci.quantity()))
                        : BigDecimal.ZERO);
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            if (firstServiceId == null) {
                for (CreateOrderItemAttendeeRequest a : ci.attendees()) {
                    if (a.serviceId() != null) { firstServiceId = a.serviceId(); break; }
                }
            }
            Package pkg = packageCache.computeIfAbsent(ci.packageId(),
                    pid -> packageRepository.findById(pid).orElse(null));
            if (pkg != null && (pkg.isCouple() || pkg.isRequiresVipRoom())) anyCoupleOrVip = true;
            ItemRoom room = resolveItemRoom(pkg, ci.roomType());
            itemRoomTypes.add(room.roomType());
            itemRoomCharges.add(room.charge());
            roomChargeTotal = roomChargeTotal.add(room.charge());
        }

        String roomType = representativeRoomType(itemRoomTypes);
        BigDecimal roomCharge = roomChargeTotal;

        BigDecimal subtotalFromRequest = request.subtotal() != null
                ? request.subtotal()
                : itemsSubtotal.add(roomChargeTotal);
        BigDecimal attendeeDiscountTotal = BigDecimal.ZERO;
        for (CreateOrderItemRequest ci : request.items()) {
            for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                if (ar.discount() != null) {
                    attendeeDiscountTotal = attendeeDiscountTotal.add(ar.discount());
                }
            }
        }
        BigDecimal requestDiscount = request.discount() != null ? request.discount() : BigDecimal.ZERO;
        BigDecimal discount = requestDiscount.add(attendeeDiscountTotal);
        BigDecimal tax = request.tax() != null ? request.tax() : BigDecimal.ZERO;
        BigDecimal total = request.total() != null
                ? request.total()
                : subtotalFromRequest.subtract(discount).add(tax).max(BigDecimal.ZERO);

        orderItemRepository.deleteAllByOrderId(order.getId());
        attendeeRepository.deleteAllByOrderId(order.getId());

        if (request.orNumber() != null && !request.orNumber().isBlank()) {
            order.setOrNumber(request.orNumber());
        } else if (order.getOrNumber() == null || order.getOrNumber().isBlank()) {
            order.setOrNumber(generateOrNumber());
        }
        order.setReferenceNumber(request.referenceNumber());
        order.setNotes(request.notes());
        order.setSubtotal(subtotalFromRequest);
        order.setDiscount(discount);
        order.setTax(tax);
        order.setTotal(total);
        order.setTransactorName(request.transactorName() != null ? request.transactorName()
                : order.getTransactorName());
        order.setGroupBooking(request.items() != null && request.items().size() > 1);
        order.setWeekend(Boolean.TRUE.equals(request.weekend()));
        order.setRoomType(roomType);
        order.setRoomTypeCharge(roomCharge);
        order.setVoucherId(request.voucherId());
        if (actorUserId != null) {
            order.setLastEditedByUserId(actorUserId);
        }
        orderRepository.save(order);

        if (request.voucherId() != null) {
            voucherService.markRedeemed(request.voucherId(), request.clientId());
        }

        AtomicInteger itemPos = new AtomicInteger(0);
        int roomIdx = 0;
        for (CreateOrderItemRequest ci : request.items()) {
            Package pkg = packageCache.computeIfAbsent(ci.packageId(),
                    pid -> packageRepository.findById(pid).orElse(null));
            boolean coupleOrVip = pkg != null && (pkg.isCouple() || pkg.isRequiresVipRoom());
            int derivedQuantity = coupleOrVip ? 1 : ci.attendees().size();
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrganizationId(organizationId);
            item.setPackageId(ci.packageId());
            item.setQuantity(derivedQuantity);
            item.setUnitPrice(ci.unitPrice() != null ? ci.unitPrice() : BigDecimal.ZERO);
            BigDecimal lt = ci.lineTotal() != null ? ci.lineTotal()
                    : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(lt);
            item.setRoomType(itemRoomTypes.get(roomIdx));
            item.setRoomTypeCharge(itemRoomCharges.get(roomIdx));
            roomIdx++;
            item.setPosition(ci.position() != null ? ci.position() : itemPos.getAndIncrement());
            orderItemRepository.save(item);

            Long sharedTierId = null;
            Long sharedServiceId = null;
            if (coupleOrVip && !ci.attendees().isEmpty()) {
                CreateOrderItemAttendeeRequest first = ci.attendees().get(0);
                sharedTierId = first.packageTierId();
                sharedServiceId = first.serviceId();
            }

            AtomicInteger attPos = new AtomicInteger(0);
            for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                OrderItemAttendee att = new OrderItemAttendee();
                att.setOrderItemId(item.getId());
                att.setOrderId(order.getId());
                att.setOrganizationId(organizationId);
                Long resolvedServiceId = coupleOrVip ? sharedServiceId : ar.serviceId();
                validateVipMassageDuration(pkg, resolvedServiceId);
                att.setServiceId(resolvedServiceId);
                att.setPackageTierId(coupleOrVip ? sharedTierId : ar.packageTierId());
                att.setLockerNumber(ar.lockerNumber());
                att.setClientGender(ar.clientGender());
                att.setPosition(ar.position() != null ? ar.position() : attPos.getAndIncrement());
                att.setDiscount(ar.discount() != null ? ar.discount() : BigDecimal.ZERO);
                att.setProvidedTsn(ar.providedTsn());
                attendeeRepository.save(att);
            }
        }

        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking != null) {
            if (firstServiceId != null) booking.setServiceId(firstServiceId);
            if (request.clientNickname() != null && !request.clientNickname().isBlank()) {
                booking.setClientNickname(request.clientNickname());
            }
            if (request.lockerNumber() != null) booking.setLockerNumber(request.lockerNumber());
            booking.setPax(request.pax() == null ? Math.max(1, totalAttendees) : request.pax());
            if (request.clientGender() != null) booking.setClientGender(request.clientGender());
            bookingRepository.save(booking);
        }

        if (request.initialPayment() != null && total.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payAmount = request.initialPayment().amount();
            if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero");
            }
            if (payAmount.compareTo(total) > 0) {
                throw new IllegalArgumentException("Payment amount exceeds order total");
            }
            UUID paymentActor = actorUserId != null ? actorUserId : order.getCashierUserId();
            String initialReference = request.initialPayment().referenceNumber();
            if (com.sumicare.pos.service.PayMongoService.supports(request.initialPayment().paymentMethod())) {
                com.sumicare.pos.service.PayMongoService.ChargeResult result = payMongoService.charge(
                        order, payAmount, request.initialPayment().paymentMethod(),
                        initialReference, request.initialPayment().paymentDetails());
                initialReference = result.intentId();
            }
            recordPaymentInternal(order, null, paymentActor, request.initialPayment().paymentMethod(),
                    payAmount, initialReference);
            if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
                order.setStatus("PAID");
                order.setFinishedAt(OffsetDateTime.now());
                materialiseAttendeeSessions(order);
            }
            orderRepository.save(order);
        }

        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse recordPayment(UUID organizationId, UUID orderId, UUID actorUserId, RecordPaymentRequest request) {
        Order order = requireOrder(organizationId, orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot add payment to a cancelled order");
        }
        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already fully paid");
        }
        if (order.getTotal().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Cannot record a payment on an order with a zero total");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        BigDecimal remaining = order.getTotal().subtract(order.getAmountPaid());
        if (request.amount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds remaining balance of " + remaining.toPlainString());
        }
        UUID sessionId = order.getBookingId() == null ? null : sessionRepository.findFirstByBookingId(order.getBookingId())
                .map(Session::getId).orElse(null);

        String referenceNumber = request.referenceNumber();
        if (com.sumicare.pos.service.PayMongoService.supports(request.paymentMethod())) {
            com.sumicare.pos.service.PayMongoService.ChargeResult result = payMongoService.charge(
                    order, request.amount(), request.paymentMethod(), referenceNumber, request.paymentDetails());
            referenceNumber = result.intentId();
        }

        recordPaymentInternal(order, sessionId, actorUserId, request.paymentMethod(), request.amount(), referenceNumber);

        if (actorUserId != null) {
            order.setLastEditedByUserId(actorUserId);
        }
        if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
            materialiseAttendeeSessions(order);
        }
        orderRepository.save(order);

        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking != null) {
            syncBookingPaymentStatus(booking, order);
        }
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public com.sumicare.cashier.dto.PayMongoInitiateResponse initiatePayMongo(
            UUID organizationId, UUID orderId, UUID actorUserId, RecordPaymentRequest request) {
        Order order = requireOrder(organizationId, orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot add payment to a cancelled order");
        }
        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already fully paid");
        }
        if (order.getTotal().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Cannot record a payment on an order with a zero total");
        }
        if (!com.sumicare.pos.service.PayMongoService.supports(request.paymentMethod())) {
            throw new IllegalArgumentException("PayMongo does not support payment method: " + request.paymentMethod());
        }
        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        BigDecimal remaining = order.getTotal().subtract(order.getAmountPaid());
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds remaining balance of " + remaining.toPlainString());
        }

        String returnPath = request.returnPath() == null || request.returnPath().isBlank()
                ? "/app/cashier" : request.returnPath();
        com.sumicare.pos.service.PayMongoService.ChargeResult result = payMongoService.initiate(
                order, amount, request.paymentMethod(), request.referenceNumber(), request.paymentDetails(), returnPath);

        if ("succeeded".equalsIgnoreCase(result.status())) {
            UUID sessionId = resolveSessionId(order);
            recordPaymentInternal(order, sessionId, actorUserId, request.paymentMethod(), amount, result.intentId());
            if (actorUserId != null) order.setLastEditedByUserId(actorUserId);
            if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
                order.setStatus("PAID");
                order.setFinishedAt(OffsetDateTime.now());
                materialiseAttendeeSessions(order);
            }
            orderRepository.save(order);
            return new com.sumicare.cashier.dto.PayMongoInitiateResponse("succeeded", result.intentId(), null);
        }

        return new com.sumicare.cashier.dto.PayMongoInitiateResponse(
                result.status(), result.intentId(), result.nextActionUrl());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse confirmPayMongo(UUID organizationId, UUID orderId, UUID actorUserId,
                                         com.sumicare.cashier.dto.PayMongoConfirmRequest request) {
        Order order = requireOrder(organizationId, orderId);
        Booking paidBooking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if ("PAID".equals(order.getStatus())) {
            return toResponse(order, paidBooking);
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot confirm a payment on a cancelled order");
        }

        String status = payMongoService.confirm(request.intentId());
        if (!"succeeded".equalsIgnoreCase(status) && !"processing".equalsIgnoreCase(status)) {
            throw new IllegalStateException("PayMongo payment was not authorized (status: " + status + ")");
        }

        settleGatewayPayment(order, actorUserId, request.intentId(), request.paymentMethod(), request.amount());

        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    @Transactional
    public boolean settleGatewayPayment(Order order, UUID actorUserId, String intentId,
                                        String paymentMethod, BigDecimal amount) {
        if ("PAID".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            return false;
        }
        BigDecimal remaining = order.getTotal().subtract(order.getAmountPaid());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal amt = amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? amount.min(remaining)
                : remaining;
        String method = paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "GCASH";

        UUID sessionId = resolveSessionId(order);
        recordPaymentInternal(order, sessionId, actorUserId, method, amt, intentId);
        if (actorUserId != null) order.setLastEditedByUserId(actorUserId);
        if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
            materialiseAttendeeSessions(order);
        }
        orderRepository.save(order);
        return true;
    }

    private UUID resolveSessionId(Order order) {
        return order.getBookingId() == null ? null
                : sessionRepository.findFirstByBookingId(order.getBookingId()).map(Session::getId).orElse(null);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public OrderResponse refundOrder(UUID organizationId, UUID orderId, UUID actorUserId,
                                     com.sumicare.cashier.dto.RefundRequest request) {
        Order order = requireOrder(organizationId, orderId);
        if (!"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Only a paid order can be refunded");
        }

        List<PosTransaction> gatewayTransactions = transactionRepository.findAllByOrderId(order.getId()).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .filter(t -> com.sumicare.pos.service.PayMongoService.supports(t.getPaymentMethod()))
                .toList();
        if (gatewayTransactions.isEmpty()) {
            throw new IllegalStateException("This order has no PayMongo (card or GCash) payment to refund");
        }

        BigDecimal gatewayTotal = gatewayTransactions.stream()
                .map(PosTransaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundAmount = request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) > 0
                ? request.amount()
                : gatewayTotal;
        if (refundAmount.compareTo(gatewayTotal) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds the gateway-paid total of " + gatewayTotal.toPlainString());
        }

        BigDecimal remaining = refundAmount;
        for (PosTransaction tx : gatewayTransactions) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if (tx.getReferenceNumber() == null || tx.getReferenceNumber().isBlank()) {
                throw new IllegalStateException("Payment " + tx.getReceiptNumber() + " has no gateway reference and cannot be refunded");
            }
            BigDecimal portion = remaining.min(tx.getTotal());
            com.sumicare.pos.service.PayMongoService.RefundResult result = payMongoService.refund(
                    tx.getReferenceNumber(), portion, request.reason(), request.notes(), order.getId().toString());

            TransactionLedgerEntry entry = new TransactionLedgerEntry();
            entry.setOrganizationId(order.getOrganizationId());
            entry.setTransactionId(tx.getId());
            entry.setEntryType("PAYMENT_REFUNDED");
            entry.setAmount(portion.negate());
            entry.setPaymentMethod(tx.getPaymentMethod());
            entry.setStatus("REFUND");
            entry.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"refundId\":\"" + result.refundId()
                    + "\",\"ref\":\"" + tx.getReferenceNumber() + "\"}");
            ledgerRepository.save(entry);

            tx.setStatus("REFUNDED");
            transactionRepository.save(tx);
            remaining = remaining.subtract(portion);
        }

        boolean anyGatewayRemaining = transactionRepository.findAllByOrderId(order.getId()).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .anyMatch(t -> com.sumicare.pos.service.PayMongoService.supports(t.getPaymentMethod()));
        if (!anyGatewayRemaining) {
            order.setStatus("REFUNDED");
        }
        if (actorUserId != null) order.setLastEditedByUserId(actorUserId);
        orderRepository.save(order);

        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse markPaid(UUID organizationId, UUID orderId) {
        Order order = requireOrder(organizationId, orderId);
        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already paid");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot mark a cancelled order as paid");
        }
        if (order.getAmountPaid().compareTo(order.getTotal()) < 0) {
            throw new IllegalStateException("Outstanding balance must be settled before marking paid");
        }

        order.setStatus("PAID");
        order.setFinishedAt(OffsetDateTime.now());
        materialiseAttendeeSessions(order);
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse openOrder(UUID organizationId, UUID orderId) {
        Order order = requireOrder(organizationId, orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot reopen a cancelled order");
        }

        if (hasCompletedSession(order)) {
            order.setStatus("OPEN");
            Booking completedBooking = order.getBookingId() == null ? null
                    : bookingRepository.findById(order.getBookingId()).orElse(null);
            return toResponse(order, completedBooking);
        }

        if (order.getBookingId() != null) {
            Session session = sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
            if (session != null && "ACTIVE".equals(session.getStatus())) {
                throw new IllegalStateException("Cannot reopen an order while a session is ongoing. End the session first.");
            }
        }
        for (OrderItemAttendee att : attendeeRepository.findAllByOrderIdOrderByPosition(order.getId())) {
            if (att.getSessionId() != null) {
                Session s = sessionRepository.findById(att.getSessionId()).orElse(null);
                if (s != null && "ACTIVE".equals(s.getStatus())) {
                    throw new IllegalStateException("Cannot reopen an order while a session is ongoing. End the session first.");
                }
            }
        }
        unmaterialiseAttendeeSessions(order);

        order.setStatus("OPEN");
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse cancelPayment(UUID organizationId, UUID orderId) {
        Order order = requireOrder(organizationId, orderId);
        if (!"OPEN".equals(order.getStatus())) {
            throw new IllegalStateException("Can only cancel payment on an OPEN order");
        }
        if (hasCompletedSession(order)) {
            throw new IllegalStateException("Cannot cancel payment once a session has been completed");
        }
        List<PosTransaction> transactions = transactionRepository.findAllByOrderId(order.getId());
        for (PosTransaction tx : transactions) {
            if ("REVERSED".equals(tx.getStatus()) || "REFUNDED".equals(tx.getStatus())) continue;
            String refundId = refundGatewayTransaction(tx, "requested_by_customer", order.getId());
            tx.setStatus("REVERSED");
            transactionRepository.save(tx);

            TransactionLedgerEntry reversal = new TransactionLedgerEntry();
            reversal.setOrganizationId(order.getOrganizationId());
            reversal.setTransactionId(tx.getId());
            reversal.setEntryType("PAYMENT_REVERSED");
            reversal.setAmount(tx.getTotal().negate());
            reversal.setPaymentMethod(tx.getPaymentMethod());
            reversal.setStatus("REFUND");
            reversal.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"ref\":\"" + tx.getReceiptNumber()
                    + "\",\"refundId\":\"" + (refundId == null ? "" : refundId) + "\",\"reason\":\"payment_cancelled\"}");
            ledgerRepository.save(reversal);
        }
        unmaterialiseAttendeeSessions(order);
        order.setAmountPaid(BigDecimal.ZERO);
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse cancel(UUID organizationId, UUID orderId, String reason) {
        Order order = requireOrder(organizationId, orderId);
        Session session = order.getBookingId() == null ? null
                : sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
        if (hasCompletedSession(order)) {
            throw new IllegalStateException("Cannot cancel an order whose session has already been completed");
        }
        if (order.getBookingId() != null) {
            boolean bookingCompleted = bookingRepository.findById(order.getBookingId())
                    .map(b -> "COMPLETED".equals(b.getStatus())).orElse(false);
            if (bookingCompleted) {
                throw new IllegalStateException("Cannot cancel an order whose booking has already been completed");
            }
        }
        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel a paid order. Open it first or cancel the payment.");
        }
        order.setStatus("CANCELLED");
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelledReason(reason);
        if (session != null && "ACTIVE".equals(session.getStatus())) {
            bookingService.cancelSession(organizationId, session.getId());
        } else if (session != null && session.getRoomId() != null && session.getBedId() != null) {
            occupancyService.release(organizationId, session.getRoomId(), session.getBedId());
        }
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking != null) booking.setStatus("CANCELLED");
        if (order.getTreatmentSlipId() != null) {
            slipRepository.findById(order.getTreatmentSlipId()).ifPresent(slip -> {
                slip.setStatus("VOIDED");
                slipRepository.save(slip);
            });
        }
        unmaterialiseAttendeeSessions(order);

        List<PosTransaction> transactions = transactionRepository.findAllByOrderId(order.getId());
        for (PosTransaction tx : transactions) {
            if ("REVERSED".equals(tx.getStatus()) || "REFUNDED".equals(tx.getStatus())) continue;
            String refundId = refundGatewayTransaction(tx, "requested_by_customer", order.getId());
            tx.setStatus("REVERSED");
            transactionRepository.save(tx);

            TransactionLedgerEntry reversal = new TransactionLedgerEntry();
            reversal.setOrganizationId(order.getOrganizationId());
            reversal.setTransactionId(tx.getId());
            reversal.setEntryType("ORDER_CANCELLED_REVERSAL");
            reversal.setAmount(tx.getTotal().negate());
            reversal.setPaymentMethod(tx.getPaymentMethod());
            reversal.setStatus("REFUND");
            reversal.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"ref\":\"" + tx.getReceiptNumber()
                    + "\",\"refundId\":\"" + (refundId == null ? "" : refundId) + "\",\"reason\":\"order_cancelled\"}");
            ledgerRepository.save(reversal);
        }
        order.setAmountPaid(BigDecimal.ZERO);

        return toResponse(order, booking);
    }

    @Transactional
    public boolean reconcileRefund(Order order, BigDecimal amount, String refundId) {
        List<PosTransaction> gatewayTransactions = transactionRepository.findAllByOrderId(order.getId()).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .filter(t -> com.sumicare.pos.service.PayMongoService.supports(t.getPaymentMethod()))
                .toList();
        if (gatewayTransactions.isEmpty()) {
            return false;
        }
        BigDecimal gatewayTotal = gatewayTransactions.stream()
                .map(PosTransaction::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? amount.min(gatewayTotal) : gatewayTotal;
        for (PosTransaction tx : gatewayTransactions) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal portion = remaining.min(tx.getTotal());
            TransactionLedgerEntry entry = new TransactionLedgerEntry();
            entry.setOrganizationId(order.getOrganizationId());
            entry.setTransactionId(tx.getId());
            entry.setEntryType("PAYMENT_REFUNDED");
            entry.setAmount(portion.negate());
            entry.setPaymentMethod(tx.getPaymentMethod());
            entry.setStatus("REFUND");
            entry.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"refundId\":\"" + (refundId == null ? "" : refundId)
                    + "\",\"ref\":\"" + tx.getReferenceNumber() + "\",\"source\":\"webhook\"}");
            ledgerRepository.save(entry);
            tx.setStatus("REFUNDED");
            transactionRepository.save(tx);
            remaining = remaining.subtract(portion);
        }
        boolean anyGatewayRemaining = transactionRepository.findAllByOrderId(order.getId()).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .anyMatch(t -> com.sumicare.pos.service.PayMongoService.supports(t.getPaymentMethod()));
        if (!anyGatewayRemaining) {
            order.setStatus("REFUNDED");
        }
        orderRepository.save(order);
        return true;
    }

    private String refundGatewayTransaction(PosTransaction tx, String reason, UUID orderId) {
        if (!com.sumicare.pos.service.PayMongoService.supports(tx.getPaymentMethod())) {
            return null;
        }
        if (tx.getReferenceNumber() == null || tx.getReferenceNumber().isBlank()) {
            return null;
        }
        com.sumicare.pos.service.PayMongoService.RefundResult result = payMongoService.refund(
                tx.getReferenceNumber(), tx.getTotal(), reason, null, orderId.toString());
        return result.refundId();
    }

    public List<OrderResponse> list(UUID organizationId, Collection<String> statuses) {
        List<Order> orders = (statuses == null || statuses.isEmpty())
                ? orderRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : orderRepository.findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(organizationId, statuses);
        return orders.stream().map(this::toResponseWithBookingLookup).toList();
    }

    public OrderResponse get(UUID organizationId, UUID orderId) {
        Order order = requireOrder(organizationId, orderId);
        return toResponseWithBookingLookup(order);
    }

    public OrderResponse getByBookingId(UUID organizationId, UUID bookingId) {
        Order order = orderRepository.findByBookingId(bookingId).orElseThrow();
        if (!order.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Order not in organization");
        }
        return toResponseWithBookingLookup(order);
    }

    public Order requireOrder(UUID organizationId, UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!order.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Order not in organization");
        }
        return order;
    }

    private record ItemRoom(String roomType, BigDecimal charge) {}

    private ItemRoom resolveItemRoom(Package pkg, String requested) {
        if (pkg != null && pkg.isRequiresVipRoom()) {
            return new ItemRoom("VIP", BigDecimal.ZERO);
        }
        String rt = requested != null ? requested.toUpperCase() : "COMMON";
        if (!ROOM_SURCHARGE.containsKey(rt) || "VIP".equals(rt)) {
            rt = "COMMON";
        }
        if ("PRIVATE".equals(rt)) {
            return new ItemRoom("PRIVATE", ROOM_SURCHARGE.get("PRIVATE"));
        }
        return new ItemRoom("COMMON", BigDecimal.ZERO);
    }

    private String representativeRoomType(java.util.List<String> roomTypes) {
        if (roomTypes.isEmpty()) return "COMMON";
        java.util.Set<String> distinct = new java.util.HashSet<>(roomTypes);
        if (distinct.size() == 1) return roomTypes.get(0);
        return "MIXED";
    }

    private boolean hasCompletedSession(Order order) {
        for (OrderItemAttendee att : attendeeRepository.findAllByOrderIdOrderByPosition(order.getId())) {
            if (att.getSessionId() != null) {
                boolean done = sessionRepository.findById(att.getSessionId())
                        .map(s -> "COMPLETED".equals(s.getStatus())).orElse(false);
                if (done) return true;
            }
        }
        if (order.getBookingId() != null) {
            return sessionRepository.findFirstByBookingId(order.getBookingId())
                    .map(s -> "COMPLETED".equals(s.getStatus())).orElse(false);
        }
        return false;
    }

    @Transactional
    public Order ensureForBooking(UUID organizationId, UUID bookingId, BigDecimal total) {
        return orderRepository.findByBookingId(bookingId).orElseGet(() -> {
            Order order = new Order();
            order.setOrganizationId(organizationId);
            order.setBookingId(bookingId);
            order.setTotal(total == null ? BigDecimal.ZERO : total);
            order.setSubtotal(total == null ? BigDecimal.ZERO : total);
            order.setStatus("OPEN");
            return orderRepository.save(order);
        });
    }

    @Transactional
    public Order createOpenOrderFromBooking(Booking booking, BigDecimal servicePrice) {
        return orderRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            Order order = new Order();
            order.setOrganizationId(booking.getOrganizationId());
            order.setBookingId(booking.getId());
            order.setStatus("OPEN");
            order.setSubtotal(servicePrice == null ? BigDecimal.ZERO : servicePrice);
            order.setTotal(servicePrice == null ? BigDecimal.ZERO : servicePrice);
            order.setTransactorName(booking.getClientNickname());
            order.setRoomType("COMMON");
            order.setRoomTypeCharge(BigDecimal.ZERO);
            order.setOrNumber(generateOrNumber());
            orderRepository.save(order);
            return order;
        });
    }

    private void validateVipMassageDuration(Package pkg, Long serviceId) {
        if (pkg == null || !pkg.isRequiresVipRoom() || serviceId == null) {
            return;
        }
        int duration = serviceRepository.findById(serviceId)
                .map(com.sumicare.service_catalogue.domain.Service::getDurationMinutes)
                .orElse(0);
        if (duration > 60) {
            throw new IllegalArgumentException("VIP packages allow massages up to 60 minutes only");
        }
    }

    @Transactional
    public void materialiseAttendeeSessions(Order order) {
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking == null) return;

        syncBookingPaymentStatus(booking, order);

        List<OrderItemAttendee> attendees = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
        if (attendees.isEmpty()) return;

        for (OrderItemAttendee att : attendees) {
            if (att.getSessionId() != null) continue;

            Session session = new Session();
            session.setOrganizationId(order.getOrganizationId());
            session.setBookingId(booking.getId());
            session.setStatus("PENDING");
            session.setAttendeeId(att.getId());
            session = sessionRepository.save(session);
            att.setSessionId(session.getId());
            attendeeRepository.save(att);
        }
    }

    public void syncBookingPaymentStatus(Booking booking, Order order) {
        if (booking == null || order == null) return;
        String desired = switch (order.getStatus() == null ? "" : order.getStatus()) {
            case "PAID" -> "PAID";
            case "REFUNDED" -> "REFUNDED";
            case "CANCELLED" -> "UNPAID";
            default -> {
                BigDecimal paid = order.getAmountPaid() == null ? BigDecimal.ZERO : order.getAmountPaid();
                BigDecimal total = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
                if (paid.signum() > 0 && paid.compareTo(total) < 0) {
                    yield "PARTIAL";
                }
                yield "UNPAID";
            }
        };
        if (!desired.equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus(desired);
            bookingRepository.save(booking);
        }
    }

    @Transactional
    public TreatmentSlip ensureSlipForAttendee(Order order, OrderItemAttendee attendee, Session session) {
        if (attendee.getTreatmentSlipId() != null) {
            return slipRepository.findById(attendee.getTreatmentSlipId()).orElse(null);
        }
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking == null) return null;

        OrderItem parentItem = attendee.getOrderItemId() != null
                ? orderItemRepository.findById(attendee.getOrderItemId()).orElse(null)
                : null;
        String pkgName = null;
        boolean isVip = false;
        if (parentItem != null && parentItem.getPackageId() != null) {
            Package pkg = packageRepository.findById(parentItem.getPackageId()).orElse(null);
            if (pkg != null) {
                pkgName = pkg.getName();
                isVip = pkg.isRequiresVipRoom();
            }
        }

        TreatmentSlip slip = new TreatmentSlip();
        slip.setOrganizationId(order.getOrganizationId());
        slip.setBookingId(booking.getId());
        slip.setSessionId(session != null ? session.getId() : attendee.getSessionId());
        slip.setAttendeeId(attendee.getId());
        slip.setTsn(attendee.getProvidedTsn() != null && !attendee.getProvidedTsn().isBlank()
                ? attendee.getProvidedTsn() : generateTsn());
        slip.setClientNickname(booking.getClientNickname() != null ? booking.getClientNickname() : "Walk-in");
        slip.setLockerNumber(attendee.getLockerNumber() != null ? attendee.getLockerNumber() : booking.getLockerNumber());
        slip.setOrNumber(order.getOrNumber());
        slip.setStatus("DRAFT");
        slip.setPackageName(pkgName);
        slip.setTotalAmount(order.getTotal());
        slip.setPax(booking.getPax());
        slip.setNationality(booking.getNationality());

        if (isVip) {
            slip.setVip(true);
            slip.setJacuzziMinutes(60);
            slip.setMassageMinutes(60);
            slip.setWineIncluded(true);
        }

        String resolvedServiceName = "";
        Integer resolvedDuration = null;
        Long serviceIdToResolve = attendee.getServiceId() != null ? attendee.getServiceId() : booking.getServiceId();
        if (serviceIdToResolve != null) {
            var svc = serviceRepository.findById(serviceIdToResolve).orElse(null);
            if (svc != null) {
                resolvedServiceName = svc.getName();
                resolvedDuration = svc.getDurationMinutes();
            }
        }
        slip.setServiceName(resolvedServiceName);
        if (resolvedDuration != null) {
            slip.setTreatmentMinutes(resolvedDuration);
        }

        if (session != null && session.getStartedAt() != null) {
            slip.setStartTime(session.getStartedAt());
        }

        TreatmentSlip savedSlip = slipRepository.save(slip);
        attendee.setTreatmentSlipId(savedSlip.getId());
        attendeeRepository.save(attendee);
        return savedSlip;
    }

    @Transactional
    public void unmaterialiseAttendeeSessions(Order order) {
        if (order.getBookingId() != null) {
            bookingRepository.findById(order.getBookingId())
                    .ifPresent(b -> syncBookingPaymentStatus(b, order));
        }
        List<OrderItemAttendee> attendees = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
        for (OrderItemAttendee att : attendees) {
            if (att.getSessionId() != null) {
                sessionRepository.findById(att.getSessionId()).ifPresent(s -> {
                    if (!"COMPLETED".equals(s.getStatus())) {
                        s.setStatus("CANCELLED");
                        s.setEndedAt(OffsetDateTime.now());
                        sessionRepository.save(s);
                    }
                });
                att.setSessionId(null);
            }
            if (att.getTreatmentSlipId() != null) {
                slipRepository.findById(att.getTreatmentSlipId()).ifPresent(slip -> {
                    slip.setStatus("VOIDED");
                    slipRepository.save(slip);
                });
                att.setTreatmentSlipId(null);
            }
            attendeeRepository.save(att);
        }
    }

    public void recordPaymentInternal(Order order, UUID sessionId, UUID actorUserId, String paymentMethod,
                                       BigDecimal amount, String referenceNumber) {
        PosTransaction tx = new PosTransaction();
        tx.setOrganizationId(order.getOrganizationId());
        tx.setOrderId(order.getId());
        tx.setSessionId(sessionId);
        tx.setReceiptNumber(generateReceiptNumber());
        tx.setReferenceNumber(referenceNumber);
        tx.setSubtotal(amount);
        tx.setDiscount(BigDecimal.ZERO);
        tx.setTotal(amount);
        tx.setPaymentMethod(paymentMethod);
        tx.setProcessedBy(actorUserId);
        tx.setProcessedAt(OffsetDateTime.now());
        tx.setStatus("COMPLETED");
        transactionRepository.save(tx);

        TransactionLedgerEntry entry = new TransactionLedgerEntry();
        entry.setOrganizationId(order.getOrganizationId());
        entry.setTransactionId(tx.getId());
        entry.setEntryType("PAYMENT_RECEIVED");
        entry.setAmount(amount);
        entry.setPaymentMethod(paymentMethod);
        entry.setStatus("COMPLETED");
        entry.setMetadata(referenceNumber != null
                ? "{\"orderId\":\"" + order.getId() + "\",\"ref\":\"" + referenceNumber + "\"}"
                : "{\"orderId\":\"" + order.getId() + "\"}");
        ledgerRepository.save(entry);

        order.setAmountPaid(order.getAmountPaid().add(amount));
    }

    private String generateReceiptNumber() {
        return idSequenceService.nextReceiptNumber();
    }

    public String nextOrNumber() {
        return generateOrNumber();
    }

    private String generateOrNumber() {
        return idSequenceService.nextOrNumber();
    }

    private String generateTsn() {
        return idSequenceService.nextTsn();
    }

    private OrderResponse toResponseWithBookingLookup(Order order) {
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    private OrderResponse toResponse(Order order, Booking booking) {
        BigDecimal balance = order.getTotal().subtract(order.getAmountPaid());
        String serviceName = null;
        if (booking != null) {
            serviceName = serviceRepository.findById(booking.getServiceId())
                    .map(s -> s.getName()).orElse(null);
        }
        String cashierDisplayName = null;
        if (order.getCashierUserId() != null) {
            cashierDisplayName = userRepository.findById(order.getCashierUserId())
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse(null);
        }
        String lastEditedByDisplayName = null;
        if (order.getLastEditedByUserId() != null) {
            lastEditedByDisplayName = userRepository.findById(order.getLastEditedByUserId())
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse(null);
        }
        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByPosition(order.getId());
        List<OrderItemAttendee> allAttendees = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());

        Set<UUID> sessionIds = allAttendees.stream().map(OrderItemAttendee::getSessionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Session> sessionsById = new HashMap<>();
        for (Session s : sessionRepository.findAllById(sessionIds)) {
            sessionsById.put(s.getId(), s);
        }
        Set<Long> packageIds = items.stream().map(OrderItem::getPackageId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Package> packagesById = new HashMap<>();
        for (Package p : packageRepository.findAllById(packageIds)) {
            packagesById.put(p.getId(), p);
        }

        Map<UUID, List<OrderItemAttendee>> attendeesByItem = new HashMap<>();
        boolean sessionCompleted = false;
        for (OrderItemAttendee a : allAttendees) {
            attendeesByItem.computeIfAbsent(a.getOrderItemId(), k -> new ArrayList<>()).add(a);
            if (a.getSessionId() != null) {
                Session s = sessionsById.get(a.getSessionId());
                if (s != null && "COMPLETED".equals(s.getStatus())) sessionCompleted = true;
            }
        }
        if (!sessionCompleted && order.getBookingId() != null) {
            sessionCompleted = sessionRepository.findFirstByBookingId(order.getBookingId())
                    .map(s -> "COMPLETED".equals(s.getStatus())).orElse(false);
        }
        Map<Long, String> serviceNameById = new HashMap<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        boolean orderCouplePackage = false;
        for (OrderItem it : items) {
            Package pkg = it.getPackageId() == null ? null : packagesById.get(it.getPackageId());
            String pkgName = pkg == null ? "" : pkg.getName();
            if (pkg != null && pkg.isCouple()) orderCouplePackage = true;
            List<OrderItemAttendee> atts = attendeesByItem.getOrDefault(it.getId(), List.of());
            List<OrderItemAttendeeResponse> attRes = new ArrayList<>();
            for (OrderItemAttendee a : atts) {
                String sName = a.getServiceId() == null ? null
                        : serviceNameById.computeIfAbsent(a.getServiceId(),
                            sid -> serviceRepository.findById(sid).map(s -> s.getName()).orElse(""));
                Session sess = a.getSessionId() == null ? null : sessionsById.get(a.getSessionId());
                String sessionStatus = sess == null ? null : sess.getStatus();
                boolean sessionExtended = sess != null && sess.isExtension();
                attRes.add(new OrderItemAttendeeResponse(
                        a.getId(), a.getServiceId(), sName, a.getPackageTierId(),
                        a.getLockerNumber(), a.getClientGender(),
                        a.getSessionId(), sessionStatus, sessionExtended, a.getTreatmentSlipId(), a.getPosition(),
                        a.getDiscount(), a.getProvidedTsn()
                ));
            }
            itemResponses.add(new OrderItemResponse(
                    it.getId(), it.getPackageId(), pkgName,
                    it.getQuantity(), it.getUnitPrice(), it.getLineTotal(),
                    it.getRoomType(), it.getRoomTypeCharge(), it.getPosition(),
                    attRes
            ));
        }

        return new OrderResponse(
                order.getId(),
                order.getBookingId(),
                order.getTreatmentSlipId(),
                order.getCashierUserId(),
                cashierDisplayName,
                booking != null ? booking.getClientNickname() : null,
                booking != null ? booking.getClientId() : null,
                serviceName,
                order.getOrNumber(),
                order.getReferenceNumber(),
                order.getNotes(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getTax(),
                order.getTotal(),
                order.getExtensionAmount(),
                order.getExtensionMinutes(),
                order.getAmountPaid(),
                balance,
                order.getStatus(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getFinishedAt(),
                order.getCancelledAt(),
                order.getCancelledReason(),
                order.getTransactorName(),
                order.isGroupBooking(),
                order.isWeekend(),
                order.getRoomType(),
                order.getRoomTypeCharge(),
                sessionCompleted,
                orderCouplePackage,
                lastEditedByDisplayName,
                itemResponses
        );
    }
}
