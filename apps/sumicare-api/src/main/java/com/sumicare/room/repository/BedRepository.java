/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.room.repository;

import com.sumicare.room.domain.Bed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BedRepository extends JpaRepository<Bed, UUID> {
    List<Bed> findAllByRoomId(UUID roomId);
    List<Bed> findAllByRoomIdIn(List<UUID> roomIds);
    List<Bed> findAllByRoomIdAndActiveTrue(UUID roomId);
    List<Bed> findAllByRoomIdAndActiveTrueOrderByRowIndex(UUID roomId);
}
