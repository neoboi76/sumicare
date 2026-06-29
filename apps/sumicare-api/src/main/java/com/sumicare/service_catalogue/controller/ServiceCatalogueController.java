/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.service_catalogue.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class ServiceCatalogueController {

    private final ServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;

    public ServiceCatalogueController(ServiceRepository serviceRepository, OrganizationRepository organizationRepository) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/api/services")
    public List<Service> internal(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return serviceRepository.findAllByOrganizationIdAndActiveTrue(UUID.fromString(principal.organizationId()));
    }

    @GetMapping(value = "/api/services/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        List<Service> services = serviceRepository.findAllByOrganizationIdAndActiveTrue(
                UUID.fromString(principal.organizationId()));
        StringBuilder sb = new StringBuilder();
        sb.append("Code,Name,Category,Duration (min),Price (PHP),Commission (PHP),")
                .append("Fixed Rate,Requires Two Therapists,Description\r\n");
        for (Service s : services) {
            sb.append(csvCell(s.getCode())).append(',')
                    .append(csvCell(s.getName())).append(',')
                    .append(csvCell(s.getCategory())).append(',')
                    .append(s.getDurationMinutes()).append(',')
                    .append(formatMoney(s.getPrice())).append(',')
                    .append(formatMoney(s.getCommissionAmount())).append(',')
                    .append(s.isFixedRate() ? "Yes" : "No").append(',')
                    .append(s.isRequiresTwoTherapists() ? "Yes" : "No").append(',')
                    .append(csvCell(s.getDescription()))
                    .append("\r\n");
        }
        byte[] payload = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"services-catalogue.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(payload);
    }

    @GetMapping("/api/public/services/{slug}")
    public List<Service> publicCatalogue(@PathVariable String slug) {
        return organizationRepository.findBySlug(slug)
                .map(o -> serviceRepository.findAllByOrganizationIdAndActiveTrue(o.getId()))
                .orElseGet(List::of);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }
}
