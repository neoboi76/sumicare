package com.sumicare.cashier.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.StartSessionRequest;
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
                        RoomOccupancyService occupancyService) {
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
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse create(UUID organizationId, UUID cashierUserId, CreateOrderRequest request) {
        if (request.serviceIds() == null || request.serviceIds().isEmpty()) {
            throw new IllegalArgumentException("Order must include at least one service");
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

        String nickname = request.clientNickname() != null && !request.clientNickname().isBlank()
                ? request.clientNickname()
                : "Walk-in";

        // Create booking but do NOT auto-start session.
        // Pay first before massage: session only starts after order is PAID.
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
        order.setStatus("PENDING");
        orderRepository.save(order);

        if (request.initialPayment() != null
                && request.initialPayment().amount().compareTo(BigDecimal.ZERO) > 0) {
            recordPaymentInternal(order, null, cashierUserId, request.initialPayment().paymentMethod(),
                    request.initialPayment().amount(), request.initialPayment().referenceNumber());
        }

        return toResponse(order, booking);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public OrderResponse recordPayment(UUID organizationId, UUID orderId, UUID actorUserId, RecordPaymentRequest request) {
        Order order = requireOrder(organizationId, orderId);
        if ("CANCELLED".equals(order.getStatus()) || "FINISHED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot add payment to a " + order.getStatus().toLowerCase() + " order");
        }
        UUID sessionId = sessionRepository.findFirstByBookingId(order.getBookingId())
                .map(Session::getId).orElse(null);
        recordPaymentInternal(order, sessionId, actorUserId, request.paymentMethod(), request.amount(), request.referenceNumber());
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
        if ("COMPLETED".equals(order.getStatus())) {
            throw new IllegalStateException("Order is already completed");
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
    public OrderResponse cancel(UUID organizationId, UUID orderId, String reason) {
        Order order = requireOrder(organizationId, orderId);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        order.setStatus("CANCELLED");
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelledReason(reason);
        Session session = sessionRepository.findFirstByBookingId(order.getBookingId()).orElse(null);
        if (session != null && session.getRoomId() != null && session.getBedId() != null) {
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
            if (!"COMPLETED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())) {
                order.setStatus("COMPLETED");
                order.setCompletedAt(endedAt != null ? endedAt : OffsetDateTime.now());
                orderRepository.save(order);


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
        return new OrderResponse(
                order.getId(),
                order.getBookingId(),
                order.getTreatmentSlipId(),
                order.getCashierUserId(),
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
