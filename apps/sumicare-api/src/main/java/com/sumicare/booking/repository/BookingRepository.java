package com.sumicare.booking.repository;

import com.sumicare.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByOrganizationIdAndScheduledAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
}
