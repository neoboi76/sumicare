package com.sumicare.room.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class RoomAdminController {

    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    public RoomAdminController(RoomRepository roomRepository, BedRepository bedRepository) {
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
    }

    @GetMapping("/rooms")
    public List<Room> rooms(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return roomRepository.findAllByOrganizationIdAndActiveTrue(UUID.fromString(principal.organizationId()));
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Room createRoom(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody Room room) {
        room.setOrganizationId(UUID.fromString(principal.organizationId()));
        room.setActive(true);
        return roomRepository.save(room);
    }

    @PatchMapping("/rooms/{roomId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Room updateRoom(@PathVariable UUID roomId, @RequestBody Room updates) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        if (updates.getRoomNumber() != null) room.setRoomNumber(updates.getRoomNumber());
        if (updates.getRoomType() != null) room.setRoomType(updates.getRoomType());
        if (updates.getFloor() != null) room.setFloor(updates.getFloor());
        room.setRowSegmented(updates.isRowSegmented());
        return room;
    }

    @DeleteMapping("/rooms/{roomId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivateRoom(@PathVariable UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        room.setActive(false);
    }

    @GetMapping("/rooms/{roomId}/beds")
    public List<Bed> beds(@PathVariable UUID roomId) {
        return bedRepository.findAllByRoomIdAndActiveTrue(roomId);
    }

    @PostMapping("/rooms/{roomId}/beds")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Bed createBed(@PathVariable UUID roomId, @RequestBody Bed bed) {
        bed.setRoomId(roomId);
        bed.setActive(true);
        return bedRepository.save(bed);
    }

    @DeleteMapping("/beds/{bedId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivateBed(@PathVariable UUID bedId) {
        Bed bed = bedRepository.findById(bedId).orElseThrow();
        UUID roomId = bed.getRoomId();
        bed.setActive(false);
        bedRepository.flush();
        List<Bed> remaining = bedRepository.findAllByRoomIdAndActiveTrueOrderByRowIndex(roomId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setRowIndex(i + 1);
        }
    }
}
