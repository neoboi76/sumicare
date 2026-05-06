package com.sumicare.booking.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.booking.dto.BookingResponse;
import com.sumicare.booking.dto.CreateBookingRequest;
import com.sumicare.booking.dto.SessionResponse;
import com.sumicare.booking.dto.StartSessionRequest;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.booking.service.BookingService;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final OrganizationRepository organizationRepository;
    private final SessionRepository sessionRepository;

    public BookingController(BookingService bookingService,
                             OrganizationRepository organizationRepository,
                             SessionRepository sessionRepository) {
        this.bookingService = bookingService;
        this.organizationRepository = organizationRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/api/public/bookings/{slug}")
    public BookingResponse publicBook(@PathVariable String slug, @Valid @RequestBody CreateBookingRequest request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        return bookingService.createBooking(organizationId, request);
    }

    @PostMapping("/api/bookings")
    public BookingResponse internalBook(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(UUID.fromString(principal.organizationId()), request);
    }

    @GetMapping("/api/bookings")
    public List<BookingResponse> dayBookings(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam OffsetDateTime from,
                                             @RequestParam OffsetDateTime to) {
        return bookingService.listBookingsForDay(UUID.fromString(principal.organizationId()), from, to);
    }

    @PostMapping("/api/bookings/{bookingId}/sessions")
    public SessionResponse start(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                 @PathVariable UUID bookingId,
                                 @RequestBody StartSessionRequest request) {
        return bookingService.startSession(UUID.fromString(principal.organizationId()), bookingId, request);
    }

    @PostMapping("/api/sessions/{sessionId}/end")
    public SessionResponse end(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @PathVariable UUID sessionId) {
        return bookingService.endSession(UUID.fromString(principal.organizationId()), sessionId);
    }

    @PostMapping("/api/sessions/{sessionId}/extend")
    public SessionResponse extend(@PathVariable UUID sessionId,
                                  @RequestParam int minutes) {
        return bookingService.extendSession(sessionId, minutes);
    }

    @PostMapping("/api/sessions/{sessionId}/adjust-times")
    public SessionResponse adjust(@PathVariable UUID sessionId,
                                  @RequestParam(required = false) OffsetDateTime startAt,
                                  @RequestParam(required = false) OffsetDateTime endAt) {
        return bookingService.adjustTimes(sessionId, startAt, endAt);
    }

    @GetMapping("/api/sessions/by-booking/{bookingId}")
    public SessionResponse byBooking(@PathVariable UUID bookingId) {
        return sessionRepository.findFirstByBookingId(bookingId)
                .map(s -> new SessionResponse(
                        s.getId(), s.getBookingId(),
                        s.getPrimaryTherapistId(), s.getSecondaryTherapistId(),
                        s.getRoomId(), s.getBedId(), s.isSpecificallyRequested(),
                        s.isExtension(), s.getExtensionMinutes(),
                        s.getStartedAt(), s.getEndedAt(), s.getStatus()))
                .orElseThrow();
    }
}
