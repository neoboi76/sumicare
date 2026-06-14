package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.domain.OrderItemAttendee;
import com.sumicare.cashier.repository.OrderItemAttendeeRepository;
import com.sumicare.cashier.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LockerAssignmentService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "ACTIVE");

    private final BookingRepository bookingRepository;
    private final OrderRepository orderRepository;
    private final OrderItemAttendeeRepository attendeeRepository;
    private final int min;
    private final int max;

    public LockerAssignmentService(BookingRepository bookingRepository,
                                   OrderRepository orderRepository,
                                   OrderItemAttendeeRepository attendeeRepository,
                                   @Value("${sumicare.locker.min:1}") int min,
                                   @Value("${sumicare.locker.max:200}") int max) {
        this.bookingRepository = bookingRepository;
        this.orderRepository = orderRepository;
        this.attendeeRepository = attendeeRepository;
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    public Set<String> takenLockersForDay(UUID organizationId, OffsetDateTime scheduledAt) {
        OffsetDateTime reference = scheduledAt == null ? OffsetDateTime.now() : scheduledAt;
        LocalDate date = reference.atZoneSameInstant(MANILA).toLocalDate();
        OffsetDateTime from = date.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();

        Set<String> taken = new HashSet<>();
        for (Booking booking : bookingRepository.findAllByOrganizationIdAndScheduledAtBetween(organizationId, from, to)) {
            if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
                continue;
            }
            addKey(taken, booking.getLockerNumber());
            Order order = orderRepository.findByBookingId(booking.getId()).orElse(null);
            if (order == null) {
                continue;
            }
            for (OrderItemAttendee attendee : attendeeRepository.findAllByOrderIdOrderByPosition(order.getId())) {
                addKey(taken, attendee.getLockerNumber());
            }
        }
        return taken;
    }

    public String assign(String gender, Set<String> taken) {
        String prefix = prefixFor(gender);
        int span = max - min + 1;
        for (int attempt = 0; attempt < span * 2; attempt++) {
            int number = ThreadLocalRandom.current().nextInt(min, max + 1);
            if (taken.add(numericKey(number))) {
                return prefix + number;
            }
        }
        for (int number = min; number <= max; number++) {
            if (taken.add(numericKey(number))) {
                return prefix + number;
            }
        }
        return prefix + ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private String prefixFor(String gender) {
        String normalised = gender == null ? "" : gender.trim().toUpperCase();
        return normalised.equals("M") || normalised.equals("F") ? normalised : "";
    }

    private void addKey(Set<String> taken, String locker) {
        if (locker == null || locker.isBlank()) {
            return;
        }
        String digits = locker.trim().replaceFirst("^[MFmf]", "").trim();
        if (!digits.isEmpty()) {
            taken.add(numericKey(digits));
        }
    }

    private String numericKey(int number) {
        return Integer.toString(number);
    }

    private String numericKey(String digits) {
        try {
            return Integer.toString(Integer.parseInt(digits));
        } catch (NumberFormatException ex) {
            return digits.toUpperCase();
        }
    }
}
