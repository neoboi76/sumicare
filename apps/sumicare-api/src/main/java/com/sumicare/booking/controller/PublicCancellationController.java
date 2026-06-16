/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.controller;

import com.sumicare.booking.dto.CancellationDetailsResponse;
import com.sumicare.booking.dto.CancellationRequestRequest;
import com.sumicare.booking.dto.CancellationVerifyRequest;
import com.sumicare.booking.service.BookingCancellationService;
import com.sumicare.organization.repository.OrganizationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class PublicCancellationController {

    private final OrganizationRepository organizationRepository;
    private final BookingCancellationService cancellationService;

    public PublicCancellationController(OrganizationRepository organizationRepository,
                                        BookingCancellationService cancellationService) {
        this.organizationRepository = organizationRepository;
        this.cancellationService = cancellationService;
    }

    @PostMapping("/api/public/bookings/{slug}/cancel/request")
    public Map<String, String> request(@PathVariable String slug,
                                       @Valid @RequestBody CancellationRequestRequest request,
                                       HttpServletRequest httpRequest) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        cancellationService.requestCancellation(organizationId, request.reference(), request.email(), clientIp(httpRequest));
        return Map.of("message", "If a matching reservation exists, a cancellation code has been sent to the email on file.");
    }

    @PostMapping("/api/public/bookings/{slug}/cancel/verify")
    public CancellationDetailsResponse verify(@PathVariable String slug,
                                              @Valid @RequestBody CancellationVerifyRequest request) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        return cancellationService.verify(organizationId, request.reference(), request.email(), request.code());
    }

    @PostMapping("/api/public/bookings/{slug}/cancel/confirm")
    public Map<String, String> confirm(@PathVariable String slug,
                                       @Valid @RequestBody CancellationVerifyRequest request,
                                       HttpServletRequest httpRequest) {
        UUID organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        cancellationService.confirm(organizationId, request.reference(), request.email(), request.code(), clientIp(httpRequest));
        return Map.of("message", "Your reservation has been cancelled.");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
