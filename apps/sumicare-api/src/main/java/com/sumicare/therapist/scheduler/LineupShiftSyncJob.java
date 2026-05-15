package com.sumicare.therapist.scheduler;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.domain.ShiftAssignment;
import com.sumicare.shift.repository.ShiftAssignmentRepository;
import com.sumicare.shift.service.ShiftService;
import com.sumicare.therapist.dto.DeckingEntry;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.DeckingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LineupShiftSyncJob {

    private static final Logger log = LoggerFactory.getLogger(LineupShiftSyncJob.class);

    private final OrganizationRepository organizationRepository;
    private final ShiftService shiftService;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final TherapistRepository therapistRepository;
    private final DeckingService deckingService;

    public LineupShiftSyncJob(OrganizationRepository organizationRepository,
                              ShiftService shiftService,
                              ShiftAssignmentRepository shiftAssignmentRepository,
                              TherapistRepository therapistRepository,
                              DeckingService deckingService) {
        this.organizationRepository = organizationRepository;
        this.shiftService = shiftService;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.therapistRepository = therapistRepository;
        this.deckingService = deckingService;
    }

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    public void syncLineup() {
        LocalTime now = LocalTime.now(MANILA);
        try {
            organizationRepository.findAll().forEach(org -> syncForOrganization(org.getId(), now));
        } catch (Exception e) {
            log.error("LineupShiftSyncJob failed", e);
        }
    }

    public void syncNow(UUID organizationId) {
        try {
            syncForOrganization(organizationId, LocalTime.now(MANILA));
        } catch (Exception e) {
            log.error("LineupShiftSyncJob.syncNow failed", e);
        }
    }

    private void syncForOrganization(UUID orgId, LocalTime now) {
        List<Shift> activeShifts = shiftService.listActive(orgId).stream()
                .filter(s -> shiftService.coversTime(s.getStartTime(), s.getEndTime(), now))
                .toList();

        Map<UUID, Long> shouldBeInLineup = new HashMap<>();
        for (Shift shift : activeShifts) {
            for (ShiftAssignment sa : shiftAssignmentRepository.findAllByShiftId(shift.getId())) {
                therapistRepository.findById(sa.getTherapistId())
                        .filter(t -> t.isActive() && !t.isBackup())
                        .filter(t -> orgId.equals(t.getOrganizationId()))
                        .ifPresent(t -> shouldBeInLineup.putIfAbsent(t.getId(), shift.getId()));
            }
        }

        List<DeckingEntry> currentLineup = deckingService.currentLineup(orgId);
        Set<UUID> currentIds = new HashSet<>();
        for (DeckingEntry e : currentLineup) currentIds.add(e.therapistId());

        for (Map.Entry<UUID, Long> entry : shouldBeInLineup.entrySet()) {
            if (!currentIds.contains(entry.getKey())) {
                deckingService.appendToBack(orgId, entry.getKey(), entry.getValue());
            }
        }

        for (DeckingEntry e : currentLineup) {
            if ("BACKUP".equals(e.flag()) || "MANUAL".equals(e.flag())) continue;
            if (!shouldBeInLineup.containsKey(e.therapistId())) {
                deckingService.remove(orgId, e.therapistId());
            }
        }
    }
}
