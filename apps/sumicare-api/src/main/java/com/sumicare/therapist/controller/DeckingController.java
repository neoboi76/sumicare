package com.sumicare.therapist.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.repository.ShiftAssignmentRepository;
import com.sumicare.shift.repository.ShiftRepository;
import com.sumicare.therapist.dto.DeckingEntry;
import com.sumicare.therapist.dto.LineupTherapistResponse;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.DeckingService;
import com.sumicare.therapist.service.DeckingService.DeckingFlag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/decking")
public class DeckingController {

    private final DeckingService deckingService;
    private final TherapistRepository therapistRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;

    public DeckingController(DeckingService deckingService,
                             TherapistRepository therapistRepository,
                             ShiftAssignmentRepository shiftAssignmentRepository,
                             ShiftRepository shiftRepository) {
        this.deckingService = deckingService;
        this.therapistRepository = therapistRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
    }

    @GetMapping
    public List<DeckingEntry> lineup(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return deckingService.currentLineup(UUID.fromString(principal.organizationId()));
    }

    @GetMapping("/lineup")
    public List<LineupTherapistResponse> lineupWithDetails(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<DeckingEntry> entries = deckingService.currentLineup(orgId);
        Map<Long, String> shiftLabels = new HashMap<>();
        shiftRepository.findAllByOrganizationIdAndActiveTrue(orgId)
                .forEach(s -> shiftLabels.put(s.getId(), formatShiftLabel(s)));
        AtomicInteger pos = new AtomicInteger(0);
        return entries.stream()
                .filter(e -> !"BACKUP".equals(e.flag()))
                .map(e -> therapistRepository.findById(e.therapistId())
                        .map(t -> {
                            String shiftLabel = shiftAssignmentRepository.findAllByTherapistId(t.getId()).stream()
                                    .map(sa -> shiftLabels.get(sa.getShiftId()))
                                    .filter(Objects::nonNull)
                                    .findFirst().orElse(null);
                            return new LineupTherapistResponse(
                                    t.getId(), t.getNickname(), t.getGender(),
                                    shiftLabel, e.flag(), e.skipped(), pos.incrementAndGet());
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private String formatShiftLabel(Shift s) {
        return s.getLabel() + " (" + s.getStartTime() + "–" + s.getEndTime() + ")";
    }

    @PostMapping("/{therapistId}/skip")
    public void skip(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                     @PathVariable UUID therapistId,
                     @RequestParam(defaultValue = "30") int minutes) {
        deckingService.skip(UUID.fromString(principal.organizationId()), therapistId,
                Duration.ofMinutes(Math.min(minutes, 30)));
    }

    @PostMapping("/{therapistId}/skip/cancel")
    public void cancelSkip(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                           @PathVariable UUID therapistId) {
        deckingService.cancelSkip(UUID.fromString(principal.organizationId()), therapistId);
    }

    @PostMapping("/{therapistId}/flag")
    public void setFlag(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                        @PathVariable UUID therapistId,
                        @RequestParam DeckingFlag flag) {
        deckingService.setFlag(UUID.fromString(principal.organizationId()), therapistId, flag);
    }

    @PostMapping("/{therapistId}/rotate")
    public void rotateToBack(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                             @PathVariable UUID therapistId) {
        deckingService.rotateToBack(UUID.fromString(principal.organizationId()), therapistId);
    }

    @PostMapping("/backup/{therapistId}")
    public void insertBackup(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                             @PathVariable UUID therapistId,
                             @RequestParam(defaultValue = "0") int position) {
        deckingService.insertBackup(UUID.fromString(principal.organizationId()), therapistId, position);
    }

    @DeleteMapping("/{therapistId}")
    public void remove(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                       @PathVariable UUID therapistId) {
        deckingService.remove(UUID.fromString(principal.organizationId()), therapistId);
    }
}
