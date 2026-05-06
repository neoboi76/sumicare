package com.sumicare.therapist.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.therapist.dto.DeckingEntry;
import com.sumicare.therapist.service.DeckingService;
import com.sumicare.therapist.service.DeckingService.DeckingFlag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/decking")
public class DeckingController {

    private final DeckingService deckingService;

    public DeckingController(DeckingService deckingService) {
        this.deckingService = deckingService;
    }

    @GetMapping
    public List<DeckingEntry> lineup(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return deckingService.currentLineup(UUID.fromString(principal.organizationId()));
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
