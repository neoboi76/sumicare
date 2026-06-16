/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByBookingId(UUID bookingId);
    List<Order> findAllByBookingIdIn(Collection<UUID> bookingIds);
    @Query("SELECT o FROM Order o WHERE o.organizationId = :organizationId AND o.orNumber = :orNumber")
    Optional<Order> findByOrganizationIdAndOrNumber(UUID organizationId, String orNumber);
    List<Order> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<Order> findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(UUID organizationId, Collection<String> statuses);
    List<Order> findAllByOrganizationIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
}
