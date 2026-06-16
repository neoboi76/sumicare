/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.shift.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.repository.ShiftRepository;
import com.sumicare.shift.service.ShiftService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftRepository shiftRepository;

    public ShiftController(ShiftService shiftService, ShiftRepository shiftRepository) {
        this.shiftService = shiftService;
        this.shiftRepository = shiftRepository;
    }

    @GetMapping
    public List<Shift> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return shiftService.listActive(UUID.fromString(principal.organizationId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Shift create(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody Shift shift) {
        shift.setOrganizationId(UUID.fromString(principal.organizationId()));
        shift.setActive(true);
        return shiftRepository.save(shift);
    }

    @PatchMapping("/{shiftId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public Shift update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                        @PathVariable Long shiftId, @RequestBody Shift updates) {
        Shift shift = requireSameOrg(principal, shiftId);
        if (updates.getLabel() != null) shift.setLabel(updates.getLabel());
        if (updates.getStartTime() != null) shift.setStartTime(updates.getStartTime());
        if (updates.getEndTime() != null) shift.setEndTime(updates.getEndTime());
        if (updates.getExpectedCount() != null) shift.setExpectedCount(updates.getExpectedCount());
        return shift;
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                           @PathVariable Long shiftId) {
        requireSameOrg(principal, shiftId).setActive(false);
    }

    private Shift requireSameOrg(AuthenticatedPrincipal principal, Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId).orElseThrow();
        if (!shift.getOrganizationId().equals(UUID.fromString(principal.organizationId()))) {
            throw new AccessDeniedException("Shift belongs to another organization.");
        }
        return shift;
    }
}
