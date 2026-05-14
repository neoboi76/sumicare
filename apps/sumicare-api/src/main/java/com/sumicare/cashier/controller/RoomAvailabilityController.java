package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.room.domain.Room;
import com.sumicare.room.repository.RoomRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier/room-availability")
public class RoomAvailabilityController {

    private final RoomRepository roomRepository;

    public RoomAvailabilityController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public Map<String, Integer> availability(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<Room> rooms = roomRepository.findAllByOrganizationId(orgId);
        Map<String, Integer> counts = new HashMap<>();
        counts.put("COMMON", 0);
        counts.put("PRIVATE", 0);
        counts.put("VIP", 0);
        for (Room r : rooms) {
            if (!r.isActive()) continue;
            String type = r.getRoomType() == null ? "" : r.getRoomType().toUpperCase();
            String key;
            if (type.contains("VIP")) {
                key = "VIP";
            } else if (type.contains("PRIVATE")) {
                key = "PRIVATE";
            } else {
                key = "COMMON";
            }
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}
