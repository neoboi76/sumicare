package com.sumicare.booking.repository;

import com.sumicare.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByOrganizationIdAndScheduledAtBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);

    @Query("select b from Booking b where b.organizationId = :organizationId "
            + "and coalesce(b.actualStartAt, b.scheduledAt) >= :from "
            + "and coalesce(b.actualStartAt, b.scheduledAt) < :to")
    List<Booking> findAllByOrganizationIdAndEffectiveDateBetween(@Param("organizationId") UUID organizationId,
                                                                 @Param("from") OffsetDateTime from,
                                                                 @Param("to") OffsetDateTime to);

    boolean existsByOrganizationIdAndClientNicknameIgnoreCaseAndStatusIn(UUID organizationId, String clientNickname, List<String> statuses);
}
