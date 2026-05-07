package com.sumicare.booking.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.dto.CreateWalkInRequest;
import com.sumicare.booking.dto.WalkInResponse;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.notification.service.NotificationService;
import com.sumicare.room.domain.Bed;
import com.sumicare.room.exception.RoomGenderConflictException;
import com.sumicare.room.repository.BedRepository;
import com.sumicare.room.repository.RoomRepository;
import com.sumicare.room.service.RoomOccupancyService;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.therapist.domain.Therapist;
import com.sumicare.therapist.repository.TherapistRepository;
import com.sumicare.therapist.service.DeckingService;
import com.sumicare.transaction.domain.TreatmentSlip;
import com.sumicare.transaction.repository.TreatmentSlipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class WalkInService {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final ServiceRepository serviceRepository;
    private final TherapistRepository therapistRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final TreatmentSlipRepository slipRepository;
    private final RoomOccupancyService occupancyService;
    private final DeckingService deckingService;
    private final NotificationService notificationService;

    public WalkInService(BookingRepository bookingRepository,
                         SessionRepository sessionRepository,
                         ServiceRepository serviceRepository,
                         TherapistRepository therapistRepository,
                         RoomRepository roomRepository,
                         BedRepository bedRepository,
                         TreatmentSlipRepository slipRepository,
                         RoomOccupancyService occupancyService,
                         DeckingService deckingService,
                         NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRepository = serviceRepository;
        this.therapistRepository = therapistRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.slipRepository = slipRepository;
        this.occupancyService = occupancyService;
        this.deckingService = deckingService;
        this.notificationService = notificationService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public WalkInResponse createWalkIn(UUID organizationId, CreateWalkInRequest request) {
        Service service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + request.serviceId()));

        OffsetDateTime startTime = request.startTime();
        OffsetDateTime expectedEnd = request.endTime() != null
                ? request.endTime()
                : startTime.plusMinutes(service.getDurationMinutes());

        List<UUID> bedIds = request.bedIds() != null ? request.bedIds() : List.of();

        if (request.roomId() != null && !bedIds.isEmpty() && request.clientGender() != null) {
            enforceGenderLock(organizationId, request.roomId(), bedIds, request.clientGender());
        }

        Booking booking = new Booking();
        booking.setOrganizationId(organizationId);
        booking.setClientNickname(request.clientNickname());
        booking.setLockerNumber(request.lockerNumber());
        booking.setServiceId(request.serviceId());
        booking.setReservationType(request.reservationType() != null ? request.reservationType() : "WALK_IN");
        booking.setScheduledAt(startTime);
        booking.setActualStartAt(startTime);
        booking.setPax(request.pax());
        booking.setClientGender(request.clientGender());
        booking.setStatus("ACTIVE");
        bookingRepository.save(booking);

        UUID primaryBedId = bedIds.isEmpty() ? null : bedIds.get(0);

        Session session = new Session();
        session.setOrganizationId(organizationId);
        session.setBookingId(booking.getId());
        session.setPrimaryTherapistId(request.primaryTherapistId());
        session.setSecondaryTherapistId(request.secondaryTherapistId());
        session.setRoomId(request.roomId());
        session.setBedId(primaryBedId);
        session.setSpecificallyRequested(request.specificallyRequested());
        session.setStartedAt(startTime);
        session.setExpectedEndAt(expectedEnd);
        session.setStatus("ACTIVE");
        sessionRepository.save(session);

        if (request.roomId() != null && !bedIds.isEmpty()) {
            String therapistNickname = request.primaryTherapistId() == null ? "" :
                    therapistRepository.findById(request.primaryTherapistId())
                            .map(Therapist::getNickname).orElse("");
            for (UUID bedId : bedIds) {
                occupancyService.occupy(organizationId, request.roomId(), bedId,
                        request.clientNickname(), request.lockerNumber(), therapistNickname,
                        request.clientGender());
                notificationService.broadcastRoomUpdate(organizationId, request.roomId(), bedId,
                        Map.of("event", "SESSION_STARTED", "sessionId", session.getId()));
            }
        }

        if (request.primaryTherapistId() != null) {
            if (request.specificallyRequested()) {
                deckingService.servedRequested(organizationId, request.primaryTherapistId());
            } else {
                deckingService.rotateToBack(organizationId, request.primaryTherapistId());
            }
        }

        TreatmentSlip slip = new TreatmentSlip();
        slip.setOrganizationId(organizationId);
        slip.setBookingId(booking.getId());
        slip.setSessionId(session.getId());
        slip.setTsn(generateTsn());
        slip.setClientNickname(request.clientNickname());
        slip.setLockerNumber(request.lockerNumber());
        slip.setServiceName(service.getName());
        slip.setStartTime(startTime);
        slip.setEndTime(expectedEnd);
        slip.setVip(service.isVip());
        slip.setPax(request.pax());
        slip.setOrNumber(request.orNumber());
        slip.setAddOnOrNumber(request.addOnOrNumber());
        slip.setOthersAddOn(request.othersAddOn());
        slip.setRemarks(request.remarks());
        slip.setTotalAmount(request.totalAmount());

        if (service.isVip()) {
            slip.setJacuzziMinutes(request.jacuzziMinutes());
            slip.setMassageMinutes(request.massageMinutes());
            slip.setWineIncluded(request.wineIncluded());
        } else {
            slip.setTreatmentMinutes(service.getDurationMinutes());
        }

        if (request.waiverAccepted()) {
            slip.setWaiverAccepted(true);
            slip.setWaiverAcceptedAt(OffsetDateTime.now());
        }

        if (request.primaryTherapistId() != null) {
            therapistRepository.findById(request.primaryTherapistId()).ifPresent(t -> {
                slip.setPrimaryTherapistNickname(t.getNickname());
                if (request.specificallyRequested()) {
                    slip.setRequestedTherapistNickname(t.getNickname());
                }
            });
        }
        if (request.secondaryTherapistId() != null) {
            therapistRepository.findById(request.secondaryTherapistId())
                    .ifPresent(t -> slip.setSecondaryTherapistNickname(t.getNickname()));
        }
        if (request.roomId() != null) {
            roomRepository.findById(request.roomId())
                    .ifPresent(r -> slip.setRoomNumber(r.getRoomNumber()));
        }

        TreatmentSlip saved = slipRepository.save(slip);
        return new WalkInResponse(saved.getId(), booking.getId(), session.getId(), saved.getTsn());
    }

    private void enforceGenderLock(UUID organizationId, UUID roomId, List<UUID> bedIds, String clientGender) {
        var roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) return;
        var room = roomOpt.get();

        if ("PRIVATE".equalsIgnoreCase(room.getRoomType()) || "VIP".equalsIgnoreCase(room.getRoomType())) {
            return;
        }

        List<Bed> allBeds = bedRepository.findAllByRoomIdAndActiveTrue(roomId);

        for (Bed bed : allBeds) {
            if (bedIds.contains(bed.getId())) continue;

            Map<Object, Object> occupancy = occupancyService.read(roomId, bed.getId());
            String existingLock = (String) occupancy.get("genderLock");
            if (existingLock == null || existingLock.isBlank()) continue;

            if (room.isRowSegmented()) {
                boolean sameRow = bedIds.stream().anyMatch(reqBedId ->
                        allBeds.stream()
                                .filter(b -> b.getId().equals(reqBedId))
                                .findFirst()
                                .map(b -> b.getRowIndex() != null && b.getRowIndex().equals(bed.getRowIndex()))
                                .orElse(false));
                if (!sameRow) continue;
            }

            if (!existingLock.equals(clientGender)) {
                String label = "F".equals(existingLock) ? "Female" : "Male";
                throw new RoomGenderConflictException(label);
            }
        }
    }

    private String generateTsn() {
        return "TS" + System.currentTimeMillis() % 100000;
    }
}
