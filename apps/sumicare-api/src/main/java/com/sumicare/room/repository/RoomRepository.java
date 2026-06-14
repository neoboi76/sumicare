package com.sumicare.room.repository;

import com.sumicare.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    List<Room> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
    List<Room> findAllByOrganizationId(UUID organizationId);
}
