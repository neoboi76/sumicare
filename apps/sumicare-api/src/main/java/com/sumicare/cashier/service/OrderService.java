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
                        PackageRepository packageRepository) {
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
        BigDecimal discount = request.discount() != null ? request.discount() : BigDecimal.ZERO;
        BigDecimal total = request.total() != null
                ? request.total()
                : subtotalFromRequest.subtract(discount).max(BigDecimal.ZERO);

        String nickname = request.clientNickname() != null ? request.clientNickname() : request.transactorName();
        if (nickname == null || nickname.isBlank()) nickname = "Walk-in";

        var bookingRequest = new CreateBookingRequest(
                request.clientId(),
                nickname,
                request.lockerNumber(),
                firstServiceId,
                "WALK_IN",
                OffsetDateTime.now(),
                request.pax() == null ? Math.max(1, totalAttendees) : request.pax(),
                request.clientGender()
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
        if (order.getOrNumber() == null || order.getOrNumber().isBlank()) {
            order.setOrNumber(generateOrNumber());
        }
        order.setReferenceNumber(request.referenceNumber());
        order.setNotes(request.notes());
        order.setSubtotal(subtotalFromRequest);
        order.setDiscount(discount);
        order.setTotal(total);
        order.setAmountPaid(BigDecimal.ZERO);
        order.setStatus("OPEN");
        order.setTransactorName(request.transactorName() != null ? request.transactorName() : nickname);
        order.setGroupBooking(Boolean.TRUE.equals(request.groupBooking()) || totalAttendees > 1);
        order.setWeekend(Boolean.TRUE.equals(request.weekend()));
        order.setRoomType(roomType);
        order.setRoomTypeCharge(roomCharge);
        orderRepository.save(order);

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
                    attendeeRepository.save(att);
                }
            }
        }

        if (request.initialPayment() != null
                && request.initialPayment().amount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payAmount = request.initialPayment().amount();
            if (payAmount.compareTo(total) > 0) {
                throw new IllegalArgumentException("Payment amount exceeds order total");
            }
            recordPaymentInternal(order, null, cashierUserId, request.initialPayment().paymentMethod(),
                    payAmount, request.initialPayment().referenceNumber());
            if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
                order.setStatus("PAID");
                order.setFinishedAt(OffsetDateTime.now());
                materialiseAttendeeSessionsAndSlips(order);
            }
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
            materialiseAttendeeSessionsAndSlips(order);
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
        materialiseAttendeeSessionsAndSlips(order);
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

        if (order.getBookingId() != null) {
            Session session = sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
            if (session != null) {
                if ("COMPLETED".equals(session.getStatus())) {
                    throw new IllegalStateException("Cannot open an order whose session has already ended");
                }
                if ("ACTIVE".equals(session.getStatus())) {
                    bookingService.cancelSession(organizationId, session.getId());
                }
            }
        }
        unmaterialiseAttendeeSessionsAndSlips(order);

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
        unmaterialiseAttendeeSessionsAndSlips(order);
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
        unmaterialiseAttendeeSessionsAndSlips(order);

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
    public void materialiseAttendeeSessionsAndSlips(Order order) {
        List<OrderItemAttendee> attendees = attendeeRepository.findAllByOrderIdOrderByPosition(order.getId());
        if (attendees.isEmpty()) return;
        Booking booking = order.getBookingId() == null ? null
                : bookingRepository.findById(order.getBookingId()).orElse(null);

        BigDecimal allItemsTotal = orderItemRepository.findAllByOrderIdOrderByPosition(order.getId()).stream()
                .map(OrderItem::getLineTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderActualTotal = order.getTotal() != null ? order.getTotal() : allItemsTotal;
        for (OrderItemAttendee att : attendees) {
            if (att.getSessionId() != null && att.getTreatmentSlipId() != null) continue;
            if (att.getServiceId() == null) continue;

            UUID sessionId = att.getSessionId();
            if (sessionId == null) {
                Session session = new Session();
                session.setOrganizationId(order.getOrganizationId());
                session.setBookingId(order.getBookingId());
                session.setStatus("ACTIVE");
                session.setStartedAt(null);
                session.setAttendeeId(att.getId());
                Session saved = sessionRepository.save(session);
                sessionId = saved.getId();
                att.setSessionId(sessionId);
            }

            UUID slipId = att.getTreatmentSlipId();
            if (slipId == null) {
                TreatmentSlip slip = new TreatmentSlip();
                slip.setOrganizationId(order.getOrganizationId());
                slip.setBookingId(order.getBookingId());
                slip.setSessionId(sessionId);
                slip.setAttendeeId(att.getId());
                slip.setTsn(generateTsn());
                slip.setStatus("DRAFT");
                slip.setLockerNumber(att.getLockerNumber());
                String svcName = serviceRepository.findById(att.getServiceId())
                        .map(s -> s.getName()).orElse("");
                slip.setServiceName(svcName);
                slip.setClientNickname(order.isGroupBooking() ? "" :
                        (booking != null ? booking.getClientNickname() : ""));
                slip.setPax(1);
                slip.setOrNumber(order.getOrNumber());
                OrderItem parentItem = orderItemRepository.findById(att.getOrderItemId()).orElse(null);
                if (parentItem != null) {
                    long attendeeCount = attendees.stream()
                            .filter(a -> parentItem.getId().equals(a.getOrderItemId()))
                            .count();
                    BigDecimal share = attendeeCount > 0
                            ? parentItem.getUnitPrice().divide(BigDecimal.valueOf(attendeeCount), 2, java.math.RoundingMode.HALF_UP)
                            : parentItem.getUnitPrice();
                    if (allItemsTotal.compareTo(BigDecimal.ZERO) > 0
                            && orderActualTotal.compareTo(allItemsTotal) != 0) {
                        share = share.multiply(orderActualTotal)
                                .divide(allItemsTotal, 2, java.math.RoundingMode.HALF_UP);
                    }
                    slip.setTotalAmount(share);
                    boolean vipPackage = packageRepository.findById(parentItem.getPackageId())
                            .map(Package::isRequiresVipRoom).orElse(false);
                    if (vipPackage) {
                        slip.setVip(true);
                        slip.setJacuzziMinutes(60);
                        slip.setMassageMinutes(60);
                    }
                }
                TreatmentSlip savedSlip = slipRepository.save(slip);
                slipId = savedSlip.getId();
                att.setTreatmentSlipId(slipId);
                if (order.getTreatmentSlipId() == null) {
                    order.setTreatmentSlipId(slipId);
                }
            }
            attendeeRepository.save(att);
        }
    }

    @Transactional
    public void unmaterialiseAttendeeSessionsAndSlips(Order order) {
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

    private void recordPaymentInternal(Order order, UUID sessionId, UUID actorUserId, String paymentMethod,
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
        for (OrderItemAttendee a : attendeeRepository.findAllByOrderIdOrderByPosition(order.getId())) {
            attendeesByItem.computeIfAbsent(a.getOrderItemId(), k -> new ArrayList<>()).add(a);
        }
        Map<Long, String> packageNameById = new HashMap<>();
        Map<Long, String> serviceNameById = new HashMap<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem it : items) {
            String pkgName = packageNameById.computeIfAbsent(it.getPackageId(),
                    pid -> packageRepository.findById(pid).map(p -> p.getName()).orElse(""));
            List<OrderItemAttendee> atts = attendeesByItem.getOrDefault(it.getId(), List.of());
            List<OrderItemAttendeeResponse> attRes = new ArrayList<>();
            for (OrderItemAttendee a : atts) {
                String sName = a.getServiceId() == null ? null
                        : serviceNameById.computeIfAbsent(a.getServiceId(),
                            sid -> serviceRepository.findById(sid).map(s -> s.getName()).orElse(""));
                attRes.add(new OrderItemAttendeeResponse(
                        a.getId(), a.getServiceId(), sName, a.getPackageTierId(),
                        a.getLockerNumber(), a.getClientGender(),
                        a.getSessionId(), a.getTreatmentSlipId(), a.getPosition()
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
                itemResponses
        );
    }
}
