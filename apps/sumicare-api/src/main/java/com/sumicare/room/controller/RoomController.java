package com.sumicare.room.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.room.service.RoomOccupancyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final RoomOccupancyService occupancyService;

    public RoomController(RoomRepository roomRepository, BedRepository bedRepository,
                          RoomOccupancyService occupancyService) {
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.occupancyService = occupancyService;
    }

    @GetMapping
    public List<RoomView> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        UUID organizationId = UUID.fromString(principal.organizationId());
        List<Room> rooms = roomRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
        return rooms.stream().map(room -> {
            List<Bed> beds = bedRepository.findAllByRoomIdAndActiveTrue(room.getId());
            List<BedView> bedViews = beds.stream().map(bed -> {
                Map<String, String> normalized = new HashMap<>();
                occupancyService.read(room.getId(), bed.getId())
                        .forEach((k, v) -> normalized.put(String.valueOf(k), String.valueOf(v)));
                return new BedView(bed.getId(), bed.getBedLabel(), bed.getRowIndex(), normalized);
            }).toList();
            return new RoomView(room.getId(), room.getRoomNumber(), room.getFloor(),
                    room.getRoomType(), room.isRowSegmented(), bedViews);
        }).toList();
    }

    public record BedView(UUID id, String label, Integer rowIndex, Map<String, String> occupancy) {}
    public record RoomView(UUID id, String roomNumber, Integer floor, String roomType,
                           boolean rowSegmented, List<BedView> beds) {}
}
