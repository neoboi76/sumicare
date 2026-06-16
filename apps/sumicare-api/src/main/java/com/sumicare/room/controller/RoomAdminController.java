/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.room.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public List<Room> rooms(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        UUID organizationId = UUID.fromString(principal.organizationId());
        return includeInactive
                ? roomRepository.findAllByOrganizationId(organizationId)
                : roomRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
    }

    @GetMapping("/rooms/with-beds")
    public List<RoomWithBeds> roomsWithBeds(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        UUID organizationId = UUID.fromString(principal.organizationId());
        List<Room> rooms = includeInactive
                ? roomRepository.findAllByOrganizationId(organizationId)
                : roomRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<UUID> roomIds = rooms.stream().map(Room::getId).toList();
        Map<UUID, List<Bed>> bedsByRoom = new HashMap<>();
        for (Bed bed : bedRepository.findAllByRoomIdIn(roomIds)) {
            if (includeInactive || bed.isActive()) {
                bedsByRoom.computeIfAbsent(bed.getRoomId(), k -> new ArrayList<>()).add(bed);
            }
        }
        return rooms.stream()
                .map(r -> new RoomWithBeds(r, bedsByRoom.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    public record RoomWithBeds(Room room, List<Bed> beds) {}

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
    public Room updateRoom(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                           @PathVariable UUID roomId,
                           @RequestBody Room updates) {
        Room room = requireRoom(principal, roomId);
        if (updates.getRoomNumber() != null) room.setRoomNumber(updates.getRoomNumber());
        if (updates.getRoomType() != null) room.setRoomType(updates.getRoomType());
        if (updates.getFloor() != null) room.setFloor(updates.getFloor());
        room.setRowSegmented(updates.isRowSegmented());
        return room;
    }

    @PatchMapping("/rooms/{roomId}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Room reactivateRoom(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID roomId) {
        Room room = requireRoom(principal, roomId);
        room.setActive(true);
        return room;
    }

    @DeleteMapping("/rooms/{roomId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivateRoom(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID roomId) {
        requireRoom(principal, roomId).setActive(false);
    }

    @GetMapping("/rooms/{roomId}/beds")
    public List<Bed> beds(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                          @PathVariable UUID roomId,
                          @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        requireRoom(principal, roomId);
        return includeInactive
                ? bedRepository.findAllByRoomId(roomId)
                : bedRepository.findAllByRoomIdAndActiveTrue(roomId);
    }

    @PostMapping("/rooms/{roomId}/beds")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Bed createBed(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                         @PathVariable UUID roomId,
                         @RequestBody Bed bed) {
        requireRoom(principal, roomId);
        bed.setRoomId(roomId);
        bed.setActive(true);
        return bedRepository.save(bed);
    }

    @PatchMapping("/beds/{bedId}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Bed reactivateBed(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID bedId) {
        Bed bed = requireBed(principal, bedId);
        bed.setActive(true);
        return bed;
    }

    @DeleteMapping("/beds/{bedId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivateBed(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID bedId) {
        Bed bed = requireBed(principal, bedId);
        UUID roomId = bed.getRoomId();
        bed.setActive(false);
        bedRepository.flush();
        List<Bed> remaining = bedRepository.findAllByRoomIdAndActiveTrueOrderByRowIndex(roomId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setRowIndex(i + 1);
        }
    }

    private Room requireRoom(AuthenticatedPrincipal principal, UUID roomId) {
        UUID organizationId = UUID.fromString(principal.organizationId());
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (!organizationId.equals(room.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return room;
    }

    private Bed requireBed(AuthenticatedPrincipal principal, UUID bedId) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bed not found"));
        requireRoom(principal, bed.getRoomId());
        return bed;
    }
}
