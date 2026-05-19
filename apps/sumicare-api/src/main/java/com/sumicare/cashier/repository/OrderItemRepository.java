package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findAllByOrderIdOrderByPosition(UUID orderId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OrderItem i WHERE i.orderId = :orderId")
    void deleteAllByOrderId(@Param("orderId") UUID orderId);
}
