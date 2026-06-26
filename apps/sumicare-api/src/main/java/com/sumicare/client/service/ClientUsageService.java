/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.client.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.OrderItemRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.client.domain.Client;
import com.sumicare.client.dto.ClientUsageResponse;
import com.sumicare.client.dto.ClientUsageResponse.UsageCount;
import com.sumicare.client.dto.ClientUsageResponse.VoucherEligibility;
import com.sumicare.client.repository.ClientRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.repository.VoucherRedemptionRepository;
import com.sumicare.voucher.repository.VoucherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ClientUsageService {

    private final ClientRepository clientRepository;
    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ServiceRepository serviceRepository;
    private final PackageRepository packageRepository;
    private final TherapistRepository therapistRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;

    public ClientUsageService(ClientRepository clientRepository,
                              BookingRepository bookingRepository,
                              SessionRepository sessionRepository,
                              OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              ServiceRepository serviceRepository,
                              PackageRepository packageRepository,
                              TherapistRepository therapistRepository,
                              VoucherRepository voucherRepository,
                              VoucherRedemptionRepository voucherRedemptionRepository) {
        this.clientRepository = clientRepository;
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.serviceRepository = serviceRepository;
        this.packageRepository = packageRepository;
        this.therapistRepository = therapistRepository;
        this.voucherRepository = voucherRepository;
        this.voucherRedemptionRepository = voucherRedemptionRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public ClientUsageResponse forClient(UUID organizationId, UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!client.getOrganizationId().equals(organizationId) || client.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        List<Booking> bookings = client.getEmail() == null || client.getEmail().isBlank()
                ? List.of()
                : bookingRepository.findAllByOrganizationIdAndClientEmailIgnoreCase(organizationId, client.getEmail());
        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();

        Map<Long, Long> serviceCounts = new LinkedHashMap<>();
        Map<UUID, Long> therapistCounts = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            if (booking.getServiceId() != null) {
                serviceCounts.merge(booking.getServiceId(), 1L, Long::sum);
            }
            sessionRepository.findFirstByBookingId(booking.getId()).ifPresent(session -> {
                if (session.isSpecificallyRequested() && session.getPrimaryTherapistId() != null) {
                    therapistCounts.merge(session.getPrimaryTherapistId(), 1L, Long::sum);
                }
            });
        }

        List<Order> orders = bookingIds.isEmpty() ? List.of() : orderRepository.findAllByBookingIdIn(bookingIds);
        BigDecimal totalSpending = orders.stream()
                .map(o -> o.getAmountPaid() == null ? BigDecimal.ZERO : o.getAmountPaid())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Long> packageCounts = new LinkedHashMap<>();
        for (Order order : orders) {
            orderItemRepository.findAllByOrderIdOrderByPosition(order.getId()).forEach(item -> {
                if (item.getPackageId() != null) {
                    packageCounts.merge(item.getPackageId(), 1L, Long::sum);
                }
            });
        }

        List<UsageCount> topServices = rank(serviceCounts, id -> serviceRepository.findById(id)
                .map(s -> s.getName()).orElse("Service #" + id));
        List<UsageCount> topPackages = rank(packageCounts, id -> packageRepository.findById(id)
                .map(p -> p.getName()).orElse("Package #" + id));
        List<UsageCount> topTherapists = rank(therapistCounts, id -> therapistRepository.findById(id)
                .map(t -> t.getNickname()).orElse("Therapist"));

        List<VoucherEligibility> vouchers = voucherRepository.findAllByOrganizationId(organizationId).stream()
                .filter(Voucher::isActive)
                .map(v -> new VoucherEligibility(v.getCode(), v.getName(), v.getDiscountAmount(),
                        !voucherRedemptionRepository.existsByVoucherIdAndClientId(v.getId(), clientId)))
                .toList();

        return new ClientUsageResponse(client.getId(), client.getNickname(), client.getEmail(),
                bookings.size(), totalSpending, topServices, topPackages, topTherapists, vouchers);
    }

    private <K> List<UsageCount> rank(Map<K, Long> counts, Function<K, String> labelFn) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new UsageCount(labelFn.apply(e.getKey()), e.getValue()))
                .toList();
    }
}
