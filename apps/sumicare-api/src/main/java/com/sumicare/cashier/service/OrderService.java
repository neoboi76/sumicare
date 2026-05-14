package com.sumicare.cashier.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.booking.service.BookingService;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.dto.CreateOrderRequest;
import com.sumicare.cashier.dto.OrderResponse;
import com.sumicare.cashier.dto.RecordPaymentRequest;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import com.sumicare.pos.service.PosService;
import com.sumicare.room.service.RoomOccupancyService;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import com.sumicare.transaction.service.TreatmentSlipService;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class OrderService {

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
                        UserRepository userRepository) {
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
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse create(UUID organizationId, UUID cashierUserId, CreateOrderRequest request) {
        if (request.serviceIds() == null || request.serviceIds().isEmpty()) {
            throw new IllegalArgumentException("Order must include at least one service");
        }
        if (request.clientNickname() == null || request.clientNickname().isBlank()) {
            throw new IllegalArgumentException("Client nickname is required");
        }

        Long primaryServiceId = request.serviceIds().get(0);
        Service primaryService = serviceRepository.findById(primaryServiceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + primaryServiceId));

        BigDecimal subtotal = request.subtotal() != null
                ? request.subtotal()
                : sumServicePrices(request.serviceIds());
        BigDecimal discount = request.discount() != null ? request.discount() : BigDecimal.ZERO;
        BigDecimal total = request.total() != null
                ? request.total()
                : subtotal.subtract(discount).max(BigDecimal.ZERO);

        String nickname = request.clientNickname();

        var bookingRequest = new CreateBookingRequest(
                request.clientId(),
                nickname,
                request.lockerNumber(),
                primaryServiceId,
                "WALK_IN",
                OffsetDateTime.now(),
                request.pax() == null ? 1 : request.pax(),
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
        order.setOrNumber(request.orNumber());
        order.setReferenceNumber(request.referenceNumber());
        order.setNotes(request.notes());
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTotal(total);
        order.setAmountPaid(BigDecimal.ZERO);
        order.setStatus("OPEN");
        orderRepository.save(order);

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
        UUID sessionId = sessionRepository.findFirstByBookingId(order.getBookingId())
                .map(Session::getId).orElse(null);
        recordPaymentInternal(order, sessionId, actorUserId, request.paymentMethod(), request.amount(), request.referenceNumber());

        if (order.getAmountPaid().compareTo(order.getTotal()) >= 0) {
            order.setStatus("PAID");
            order.setFinishedAt(OffsetDateTime.now());
        }

        Booking booking = bookingRepository.findById(order.getBookingId()).orElseThrow();
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
        Booking booking = bookingRepository.findById(order.getBookingId()).orElseThrow();
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse openOrder(UUID organizationId, UUID orderId) {
        Order order = requireOrder(organizationId, orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot reopen a cancelled order");
        }

        Session session = sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
        if (session != null) {
            if ("COMPLETED".equals(session.getStatus())) {
                throw new IllegalStateException("Cannot open an order whose session has already ended");
            }
            if ("ACTIVE".equals(session.getStatus())) {
                bookingService.cancelSession(organizationId, session.getId());
            }
        }

        order.setStatus("OPEN");
        Booking booking = bookingRepository.findById(order.getBookingId()).orElseThrow();
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
        order.setAmountPaid(BigDecimal.ZERO);
        Booking booking = bookingRepository.findById(order.getBookingId()).orElseThrow();
        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse cancel(UUID organizationId, UUID orderId, String reason) {
        Order order = requireOrder(organizationId, orderId);
        Session session = sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
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
        Booking booking = bookingRepository.findById(order.getBookingId()).orElseThrow();
        booking.setStatus("CANCELLED");
        if (order.getTreatmentSlipId() != null) {
            slipRepository.findById(order.getTreatmentSlipId()).ifPresent(slip -> {
                slip.setStatus("VOIDED");
                slipRepository.save(slip);
            });
        }

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

    private BigDecimal sumServicePrices(List<Long> serviceIds) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Long id : serviceIds) {
            Service s = serviceRepository.findById(id).orElse(null);
            if (s != null && s.getPrice() != null) {
                sum = sum.add(s.getPrice());
            }
        }
        return sum;
    }

    private String generateReceiptNumber() {
        return "OR" + System.currentTimeMillis() % 1000000;
    }

    private OrderResponse toResponseWithBookingLookup(Order order) {
        Booking booking = bookingRepository.findById(order.getBookingId()).orElse(null);
        return toResponse(order, booking);
    }

    private OrderResponse toResponse(Order order, Booking booking) {
        BigDecimal balance = order.getTotal().subtract(order.getAmountPaid());
        String serviceName = null;
        if (booking != null) {
            serviceName = serviceRepository.findById(booking.getServiceId())
                    .map(Service::getName).orElse(null);
        }
        String cashierDisplayName = null;
        if (order.getCashierUserId() != null) {
            cashierDisplayName = userRepository.findById(order.getCashierUserId())
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse(null);
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
                order.getCancelledReason()
        );
    }
}
