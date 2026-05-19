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
import com.sumicare.pos.service.PosService;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final PosService posService;
    private final RoomOccupancyService occupancyService;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemAttendeeRepository attendeeRepository;
    private final PackageRepository packageRepository;
    private final com.sumicare.voucher.service.VoucherService voucherService;

    public OrderService(OrderRepository orderRepository,
                        BookingRepository bookingRepository,
                        SessionRepository sessionRepository,
                        ServiceRepository serviceRepository,
                        BookingService bookingService,
                        TreatmentSlipService slipService,
                        TreatmentSlipRepository slipRepository,
                        PosTransactionRepository transactionRepository,
                        TransactionLedgerRepository ledgerRepository,
                        PosService posService,
                        RoomOccupancyService occupancyService,
                        UserRepository userRepository,
                        OrderItemRepository orderItemRepository,
                        OrderItemAttendeeRepository attendeeRepository,
                        PackageRepository packageRepository,
                        com.sumicare.voucher.service.VoucherService voucherService) {
        this.orderRepository = orderRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.bookingService = bookingService;
        this.slipService = slipService;
        this.slipRepository = slipRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.posService = posService;
        this.occupancyService = occupancyService;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.attendeeRepository = attendeeRepository;
        this.packageRepository = packageRepository;
        this.voucherService = voucherService;
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

        String roomType = request.roomType() != null ? request.roomType().toUpperCase() : "COMMON";
        if (!ROOM_SURCHARGE.containsKey(roomType)) {
            throw new IllegalArgumentException("Unknown room type: " + roomType);
        }

        boolean anyVip = false;
        if (hasItems) {
            for (CreateOrderItemRequest ci : request.items()) {
                Package pkg = packageRepository.findById(ci.packageId()).orElse(null);
                if (pkg != null && pkg.isRequiresVipRoom()) {
                    anyVip = true;
                    break;
                }
            }
        }

        BigDecimal roomCharge;
        if (anyVip) {
            roomType = "VIP";
            roomCharge = BigDecimal.ZERO;
        } else if ("VIP".equals(roomType)) {
            roomType = "COMMON";
            roomCharge = BigDecimal.ZERO;
        } else if ("PRIVATE".equals(roomType)) {
            roomCharge = ROOM_SURCHARGE.get("PRIVATE");
        } else {
            roomType = "COMMON";
            roomCharge = BigDecimal.ZERO;
        }

        BigDecimal subtotalFromRequest = request.subtotal() != null
                ? request.subtotal()
                : itemsSubtotal.add(roomCharge);
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
        order.setGroupBooking(Boolean.TRUE.equals(request.groupBooking()) || totalAttendees > 1);
        order.setWeekend(Boolean.TRUE.equals(request.weekend()));
        order.setRoomType(roomType);
        order.setRoomTypeCharge(roomCharge);
        order.setVoucherId(request.voucherId());
        orderRepository.save(order);

        if (request.voucherId() != null) {
            voucherService.markRedeemed(request.voucherId(), request.clientId());
        }

        if (hasItems) {
            AtomicInteger itemPos = new AtomicInteger(0);
            for (CreateOrderItemRequest ci : request.items()) {
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setOrganizationId(organizationId);
                item.setPackageId(ci.packageId());
                item.setQuantity(ci.quantity() == null ? 1 : ci.quantity());
                item.setUnitPrice(ci.unitPrice() != null ? ci.unitPrice() : BigDecimal.ZERO);
                BigDecimal lt = ci.lineTotal() != null ? ci.lineTotal()
                        : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                item.setLineTotal(lt);
                item.setPosition(ci.position() != null ? ci.position() : itemPos.getAndIncrement());
                orderItemRepository.save(item);

                AtomicInteger attPos = new AtomicInteger(0);
                for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                    OrderItemAttendee att = new OrderItemAttendee();
                    att.setOrderItemId(item.getId());
                    att.setOrderId(order.getId());
                    att.setOrganizationId(organizationId);
                    att.setServiceId(ar.serviceId());
                    att.setPackageTierId(ar.packageTierId());
                    att.setLockerNumber(ar.lockerNumber());
                    att.setClientGender(ar.clientGender());
                    att.setPosition(ar.position() != null ? ar.position() : attPos.getAndIncrement());
                    att.setDiscount(ar.discount() != null ? ar.discount() : BigDecimal.ZERO);
                    att.setProvidedTsn(ar.providedTsn());
                    attendeeRepository.save(att);
                }
            }
        }

        if (request.initialPayment() != null) {
            BigDecimal payAmount = request.initialPayment().amount();
            if (payAmount.compareTo(BigDecimal.ZERO) > 0 && payAmount.compareTo(total) > 0) {
                throw new IllegalArgumentException("Payment amount exceeds order total");
            }
            recordPaymentInternal(order, null, cashierUserId, request.initialPayment().paymentMethod(),
                    payAmount, request.initialPayment().referenceNumber());
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

        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse update(UUID organizationId, UUID orderId, CreateOrderRequest request) {
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
        }

        String roomType = request.roomType() != null ? request.roomType().toUpperCase() : "COMMON";
        if (!ROOM_SURCHARGE.containsKey(roomType)) {
            throw new IllegalArgumentException("Unknown room type: " + roomType);
        }
        boolean anyVip = false;
        for (CreateOrderItemRequest ci : request.items()) {
            Package pkg = packageRepository.findById(ci.packageId()).orElse(null);
            if (pkg != null && pkg.isRequiresVipRoom()) { anyVip = true; break; }
        }
        BigDecimal roomCharge;
        if (anyVip) {
            roomType = "VIP";
            roomCharge = BigDecimal.ZERO;
        } else if ("PRIVATE".equals(roomType)) {
            roomCharge = ROOM_SURCHARGE.get("PRIVATE");
        } else {
            roomType = "COMMON";
            roomCharge = BigDecimal.ZERO;
        }

        BigDecimal subtotalFromRequest = request.subtotal() != null
                ? request.subtotal()
                : itemsSubtotal.add(roomCharge);
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
        order.setGroupBooking(Boolean.TRUE.equals(request.groupBooking()) || totalAttendees > 1);
        order.setWeekend(Boolean.TRUE.equals(request.weekend()));
        order.setRoomType(roomType);
        order.setRoomTypeCharge(roomCharge);
        order.setVoucherId(request.voucherId());
        orderRepository.save(order);

        if (request.voucherId() != null) {
            voucherService.markRedeemed(request.voucherId(), request.clientId());
        }

        AtomicInteger itemPos = new AtomicInteger(0);
        for (CreateOrderItemRequest ci : request.items()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrganizationId(organizationId);
            item.setPackageId(ci.packageId());
            item.setQuantity(ci.quantity() == null ? 1 : ci.quantity());
            item.setUnitPrice(ci.unitPrice() != null ? ci.unitPrice() : BigDecimal.ZERO);
            BigDecimal lt = ci.lineTotal() != null ? ci.lineTotal()
                    : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(lt);
            item.setPosition(ci.position() != null ? ci.position() : itemPos.getAndIncrement());
            orderItemRepository.save(item);

            AtomicInteger attPos = new AtomicInteger(0);
            for (CreateOrderItemAttendeeRequest ar : ci.attendees()) {
                OrderItemAttendee att = new OrderItemAttendee();
                att.setOrderItemId(item.getId());
                att.setOrderId(order.getId());
                att.setOrganizationId(organizationId);
                att.setServiceId(ar.serviceId());
                att.setPackageTierId(ar.packageTierId());
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

        if (request.initialPayment() != null) {
            BigDecimal payAmount = request.initialPayment().amount();
            if (payAmount.compareTo(BigDecimal.ZERO) > 0 && payAmount.compareTo(total) > 0) {
                throw new IllegalArgumentException("Payment amount exceeds order total");
            }
            UUID actorUserId = order.getCashierUserId();
            recordPaymentInternal(order, null, actorUserId, request.initialPayment().paymentMethod(),
                    payAmount, request.initialPayment().referenceNumber());
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
        BigDecimal remaining = order.getTotal().subtract(order.getAmountPaid());
        if (request.amount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds remaining balance of " + remaining.toPlainString());
        }
        UUID sessionId = order.getBookingId() == null ? null : sessionRepository.findFirstByBookingId(order.getBookingId())
                .map(Session::getId).orElse(null);
        recordPaymentInternal(order, sessionId, actorUserId, request.paymentMethod(), request.amount(), request.referenceNumber());

        if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
            materialiseAttendeeSessions(order);
        }

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
        List<PosTransaction> transactions = transactionRepository.findAllByOrderId(order.getId());
        for (PosTransaction tx : transactions) {
            if ("REVERSED".equals(tx.getStatus())) continue;
            tx.setStatus("REVERSED");
            transactionRepository.save(tx);

            TransactionLedgerEntry reversal = new TransactionLedgerEntry();
            reversal.setOrganizationId(order.getOrganizationId());
            reversal.setTransactionId(tx.getId());
            reversal.setEntryType("PAYMENT_REVERSED");
            reversal.setAmount(tx.getTotal().negate());
            reversal.setPaymentMethod(tx.getPaymentMethod());
            reversal.setStatus("REFUND");
            reversal.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"ref\":\"" + tx.getReceiptNumber() + "\",\"reason\":\"payment_cancelled\"}");
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
        if (session != null && "COMPLETED".equals(session.getStatus())) {
            throw new IllegalStateException("Cannot cancel an order whose session has already ended");
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
            if ("REVERSED".equals(tx.getStatus())) continue;
            tx.setStatus("REVERSED");
            transactionRepository.save(tx);

            TransactionLedgerEntry reversal = new TransactionLedgerEntry();
            reversal.setOrganizationId(order.getOrganizationId());
            reversal.setTransactionId(tx.getId());
            reversal.setEntryType("ORDER_CANCELLED_REVERSAL");
            reversal.setAmount(tx.getTotal().negate());
            reversal.setPaymentMethod(tx.getPaymentMethod());
            reversal.setStatus("REFUND");
            reversal.setMetadata("{\"orderId\":\"" + order.getId() + "\",\"ref\":\"" + tx.getReceiptNumber() + "\",\"reason\":\"order_cancelled\"}");
            ledgerRepository.save(reversal);
        }
        order.setAmountPaid(BigDecimal.ZERO);

        return toResponse(order, booking);
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
    public void onSessionCompleted(UUID bookingId, OffsetDateTime endedAt) {
        orderRepository.findByBookingId(bookingId).ifPresent(order -> {
            if (order.getTreatmentSlipId() != null) {
                slipRepository.findById(order.getTreatmentSlipId()).ifPresent(slip -> {
                    slip.setStatus("FINALIZED");
                    slip.setSignedAt(OffsetDateTime.now());
                    slipRepository.save(slip);
                });
            }

            Session session = sessionRepository.findFirstByBookingId(bookingId).orElse(null);
            if (session != null) {
                posService.recordCommissionsForSession(order.getOrganizationId(), session);
            }
        });
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

    @Transactional
    public void materialiseAttendeeSessions(Order order) {
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);
        if (booking == null) return;

        List<OrderItemAttendee> attendees = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
        if (attendees.isEmpty()) return;

        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByPosition(order.getId());
        Map<UUID, OrderItem> itemById = new HashMap<>();
        Map<UUID, List<OrderItemAttendee>> attendeesByItem = new HashMap<>();
        for (OrderItem item : items) {
            itemById.put(item.getId(), item);
        }
        for (OrderItemAttendee a : attendees) {
            attendeesByItem.computeIfAbsent(a.getOrderItemId(), k -> new ArrayList<>()).add(a);
        }

        Map<Long, String> packageNameCache = new HashMap<>();
        Map<Long, Boolean> packageVipCache = new HashMap<>();
        Map<UUID, BigDecimal> sharePerAttendee = new HashMap<>();
        for (OrderItem item : items) {
            List<OrderItemAttendee> bucket = attendeesByItem.getOrDefault(item.getId(), List.of());
            int n = bucket.size();
            if (n == 0) continue;
            BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
            BigDecimal even = lineTotal.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal accumulated = even.multiply(BigDecimal.valueOf(n - 1L));
            BigDecimal remainder = lineTotal.subtract(accumulated);
            for (int i = 0; i < n; i++) {
                OrderItemAttendee a = bucket.get(i);
                BigDecimal share = (i == n - 1) ? remainder : even;
                if (a.getDiscount() != null && a.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
                    share = share.subtract(a.getDiscount()).max(BigDecimal.ZERO);
                }
                sharePerAttendee.put(a.getId(), share);
            }
        }

        for (OrderItemAttendee att : attendees) {
            if (att.getSessionId() != null) continue;

            Session session = new Session();
            session.setOrganizationId(order.getOrganizationId());
            session.setBookingId(booking.getId());
            session.setStatus("PENDING");
            session.setAttendeeId(att.getId());
            session = sessionRepository.save(session);
            att.setSessionId(session.getId());

            OrderItem parentItem = att.getOrderItemId() != null ? itemById.get(att.getOrderItemId()) : null;
            String pkgName = null;
            boolean isVip = false;
            if (parentItem != null && parentItem.getPackageId() != null) {
                pkgName = packageNameCache.computeIfAbsent(parentItem.getPackageId(),
                        pid -> packageRepository.findById(pid).map(Package::getName).orElse(null));
                isVip = packageVipCache.computeIfAbsent(parentItem.getPackageId(),
                        pid -> packageRepository.findById(pid).map(Package::isRequiresVipRoom).orElse(false));
            }

            if (att.getTreatmentSlipId() == null) {
                TreatmentSlip slip = new TreatmentSlip();
                slip.setOrganizationId(order.getOrganizationId());
                slip.setBookingId(booking.getId());
                slip.setSessionId(session.getId());
                slip.setAttendeeId(att.getId());
                slip.setTsn(att.getProvidedTsn() != null && !att.getProvidedTsn().isBlank()
                        ? att.getProvidedTsn() : generateTsn());
                slip.setClientNickname(booking.getClientNickname() != null ? booking.getClientNickname() : "Walk-in");
                slip.setLockerNumber(att.getLockerNumber() != null ? att.getLockerNumber() : booking.getLockerNumber());
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
                Long serviceIdToResolve = att.getServiceId() != null ? att.getServiceId() : booking.getServiceId();
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

                TreatmentSlip savedSlip = slipRepository.save(slip);
                att.setTreatmentSlipId(savedSlip.getId());
            }

            attendeeRepository.save(att);
        }
    }

    @Transactional
    public void unmaterialiseAttendeeSessions(Order order) {
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
        return "OR" + System.currentTimeMillis() % 1000000;
    }

    public String nextOrNumber() {
        return generateOrNumber();
    }

    private String generateOrNumber() {
        return "OR" + (System.currentTimeMillis() % 100000000L);
    }

    private String generateTsn() {
        return "TS-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
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
        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByPosition(order.getId());
        Map<UUID, List<OrderItemAttendee>> attendeesByItem = new HashMap<>();
        boolean sessionCompleted = false;
        for (OrderItemAttendee a : attendeeRepository.findAllByOrderIdOrderByPosition(order.getId())) {
            attendeesByItem.computeIfAbsent(a.getOrderItemId(), k -> new ArrayList<>()).add(a);
            if (a.getSessionId() != null) {
                boolean done = sessionRepository.findById(a.getSessionId())
                        .map(s -> "COMPLETED".equals(s.getStatus())).orElse(false);
                if (done) sessionCompleted = true;
            }
        }
        if (!sessionCompleted && order.getBookingId() != null) {
            sessionCompleted = sessionRepository.findFirstByBookingId(order.getBookingId())
                    .map(s -> "COMPLETED".equals(s.getStatus())).orElse(false);
        }
        Map<Long, String> packageNameById = new HashMap<>();
        Map<Long, Boolean> packageCoupleById = new HashMap<>();
        Map<Long, String> serviceNameById = new HashMap<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        boolean orderCouplePackage = false;
        for (OrderItem it : items) {
            String pkgName = packageNameById.computeIfAbsent(it.getPackageId(),
                    pid -> packageRepository.findById(pid).map(p -> p.getName()).orElse(""));
            boolean pkgCouple = packageCoupleById.computeIfAbsent(it.getPackageId(),
                    pid -> packageRepository.findById(pid).map(Package::isCouple).orElse(false));
            if (pkgCouple) orderCouplePackage = true;
            List<OrderItemAttendee> atts = attendeesByItem.getOrDefault(it.getId(), List.of());
            List<OrderItemAttendeeResponse> attRes = new ArrayList<>();
            for (OrderItemAttendee a : atts) {
                String sName = a.getServiceId() == null ? null
                        : serviceNameById.computeIfAbsent(a.getServiceId(),
                            sid -> serviceRepository.findById(sid).map(s -> s.getName()).orElse(""));
                String sessionStatus = a.getSessionId() == null ? null
                        : sessionRepository.findById(a.getSessionId())
                                .map(s -> s.getStatus()).orElse(null);
                attRes.add(new OrderItemAttendeeResponse(
                        a.getId(), a.getServiceId(), sName, a.getPackageTierId(),
                        a.getLockerNumber(), a.getClientGender(),
                        a.getSessionId(), sessionStatus, a.getTreatmentSlipId(), a.getPosition(),
                        a.getDiscount(), a.getProvidedTsn()
                ));
            }
            itemResponses.add(new OrderItemResponse(
                    it.getId(), it.getPackageId(), pkgName,
                    it.getQuantity(), it.getUnitPrice(), it.getLineTotal(), it.getPosition(),
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
                itemResponses
        );
    }
}
