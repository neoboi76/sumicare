/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.domain.DiscountTemplate;
import com.sumicare.cashier.dto.DiscountTemplateRequest;
import com.sumicare.cashier.dto.DiscountTemplateResponse;
import com.sumicare.cashier.repository.DiscountTemplateRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier/discount-templates")
public class DiscountTemplateController {

    private final DiscountTemplateRepository repository;

    public DiscountTemplateController(DiscountTemplateRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<DiscountTemplateResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return repository.findAllByOrganizationIdOrderByName(UUID.fromString(principal.organizationId()))
                .stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public DiscountTemplateResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                           @Valid @RequestBody DiscountTemplateRequest request) {
        DiscountTemplate template = new DiscountTemplate();
        template.setOrganizationId(UUID.fromString(principal.organizationId()));
        template.setName(request.name());
        template.setAmountType(request.amountType() == null || request.amountType().isBlank()
                ? "PERCENT" : request.amountType().toUpperCase());
        template.setPercent(request.percent());
        template.setFixedAmount(request.fixedAmount());
        return toResponse(repository.save(template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public void delete(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        repository.findById(id)
                .filter(t -> t.getOrganizationId().equals(UUID.fromString(principal.organizationId())))
                .ifPresent(repository::delete);
    }

    private DiscountTemplateResponse toResponse(DiscountTemplate t) {
        return new DiscountTemplateResponse(t.getId(), t.getName(), t.getAmountType(),
                t.getPercent(), t.getFixedAmount());
    }
}
