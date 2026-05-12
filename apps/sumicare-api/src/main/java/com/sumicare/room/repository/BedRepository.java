package com.sumicare.room.repository;

import com.sumicare.room.domain.Bed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BedRepository extends JpaRepository<Bed, UUID> {
    List<Bed> findAllByRoomId(UUID roomId);
    List<Bed> findAllByRoomIdAndActiveTrue(UUID roomId);
    List<Bed> findAllByRoomIdAndActiveTrueOrderByRowIndex(UUID roomId);
}
