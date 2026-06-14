package com.sumicare.booking.service;

import com.sumicare.audit.service.AuditService;
import com.sumicare.auth.service.EmailService;
import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.dto.CancellationDetailsResponse;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.service.OrderService;
import com.sumicare.common.util.BookingReference;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingCancellationService {

    private static final Set<String> CANCELLABLE_BOOKING_STATUSES = Set.of("PENDING");
    private static final Set<String> SETTLED_ORDER_STATUSES = Set.of("CANCELLED", "REFUNDED");

    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ServiceRepository serviceRepository;
    private final BookingCancellationCodeService codeService;
    private final EmailService emailService;
    private final AuditService auditService;

    public BookingCancellationService(BookingRepository bookingRepository, OrderRepository orderRepository,
                                      OrderService orderService, ServiceRepository serviceRepository,
                                      BookingCancellationCodeService codeService, EmailService emailService,
                                      AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.serviceRepository = serviceRepository;
        this.codeService = codeService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    public void requestCancellation(UUID organizationId, String reference, String email, String ipAddress) {
        Optional<Booking> match = findCancellable(organizationId, reference, email);
        if (match.isEmpty()) {
            return;
        }
        Booking booking = match.get();
        if (codeService.onCooldown(booking.getId())) {
            return;
        }
        String code = codeService.issue(booking.getId(), email);
        boolean emailSent = emailService.sendCancellationCodeEmail(email, booking.getClientNickname(), code);
        auditService.record(organizationId, null, "PUBLIC", "BOOKING_CANCEL_REQUESTED", "BOOKING",
                booking.getId().toString(), "{\"email\":\"" + email + "\",\"emailSent\":" + emailSent + "}", ipAddress);
    }

    public CancellationDetailsResponse verify(UUID organizationId, String reference, String email, String code) {
        Booking booking = requireVerified(organizationId, reference, email, code);
        Order order = orderRepository.findByBookingId(booking.getId()).orElse(null);
        return toDetails(booking, order);
    }

    @Transactional
    public void confirm(UUID organizationId, String reference, String email, String code, String ipAddress) {
        Booking booking = requireVerified(organizationId, reference, email, code);
        Order order = orderRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new IllegalStateException("This reservation has no order to cancel"));
        boolean refunded = "PAID".equals(order.getStatus());
        String orNumber = order.getOrNumber() == null ? "" : order.getOrNumber();

        orderService.cancelInternal(organizationId, order.getId(), "Cancelled online by the client");
        codeService.clear(booking.getId());

        auditService.record(organizationId, null, "PUBLIC", "BOOKING_CANCELLED", "BOOKING",
                booking.getId().toString(),
                "{\"email\":\"" + email + "\",\"orNumber\":\"" + orNumber + "\",\"refunded\":" + refunded + "}",
                ipAddress);
    }

    private Booking requireVerified(UUID organizationId, String reference, String email, String code) {
        Booking booking = findCancellable(organizationId, reference, email)
                .orElseThrow(() -> new IllegalArgumentException("We could not find a cancellable reservation for those details."));
        if (!codeService.verify(booking.getId(), email, code)) {
            throw new IllegalArgumentException("That verification code is invalid or has expired.");
        }
        return booking;
    }

    private Optional<Booking> findCancellable(UUID organizationId, String reference, String email) {
        if (reference == null || email == null) {
            return Optional.empty();
        }
        String ref = reference.trim();
        if (ref.isBlank()) {
            return Optional.empty();
        }
        return bookingRepository.findAllByOrganizationIdAndClientEmailIgnoreCase(organizationId, email.trim()).stream()
                .filter(b -> CANCELLABLE_BOOKING_STATUSES.contains(b.getStatus()))
                .filter(b -> b.getReference() != null && b.getReference().equalsIgnoreCase(ref))
                .filter(b -> orderRepository.findByBookingId(b.getId())
                        .map(o -> !SETTLED_ORDER_STATUSES.contains(o.getStatus()))
                        .orElse(true))
                .findFirst();
    }

    private CancellationDetailsResponse toDetails(Booking booking, Order order) {
        String summary = serviceRepository.findById(booking.getServiceId())
                .map(com.sumicare.service_catalogue.domain.Service::getName)
                .orElse("Reservation");
        return new CancellationDetailsResponse(
                reference(booking),
                booking.getClientNickname(),
                booking.getScheduledAt(),
                booking.getReservationType(),
                summary,
                order == null ? null : order.getRoomType(),
                order == null ? null : order.getTotal(),
                order != null && "PAID".equals(order.getStatus()),
                booking.getRemarks());
    }

    private String reference(Booking booking) {
        return booking.getReference() != null ? booking.getReference() : BookingReference.of(booking.getId());
    }
}
