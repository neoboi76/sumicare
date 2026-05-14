package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.OrderItemAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemAttendeeRepository extends JpaRepository<OrderItemAttendee, UUID> {
    List<OrderItemAttendee> findAllByOrderItemIdOrderByPosition(UUID orderItemId);
    List<OrderItemAttendee> findAllByOrderIdOrderByPosition(UUID orderId);
    Optional<OrderItemAttendee> findBySessionId(UUID sessionId);
    Optional<OrderItemAttendee> findByTreatmentSlipId(UUID treatmentSlipId);
}
