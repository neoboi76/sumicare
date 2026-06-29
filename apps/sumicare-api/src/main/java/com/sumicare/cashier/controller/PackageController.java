/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.domain.PackageTier;
import com.sumicare.cashier.dto.PackageRequest;
import com.sumicare.cashier.dto.PackageResponse;
import com.sumicare.cashier.dto.PackageTierResponse;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.cashier.repository.PackageTierRepository;
import com.sumicare.cashier.service.PackageService;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier/packages")
public class PackageController {

    private final PackageRepository packageRepository;
    private final PackageTierRepository tierRepository;
    private final ServiceRepository serviceRepository;
    private final PackageService packageService;

    public PackageController(PackageRepository packageRepository,
                             PackageTierRepository tierRepository,
                             ServiceRepository serviceRepository,
                             PackageService packageService) {
        this.packageRepository = packageRepository;
        this.tierRepository = tierRepository;
        this.serviceRepository = serviceRepository;
        this.packageService = packageService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    public List<PackageResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        UUID orgId = UUID.fromString(principal.organizationId());
        List<Package> packages = packageRepository.findAllByOrganizationIdAndActiveTrueOrderByName(orgId);
        Map<Long, Service> serviceCache = new HashMap<>();
        List<PackageResponse> responses = new ArrayList<>();
        for (Package pkg : packages) {
            List<PackageTier> tiers = tierRepository.findAllByPackageId(pkg.getId());
            List<PackageTierResponse> tierResponses = new ArrayList<>();
            for (PackageTier t : tiers) {
                String code = null;
                String name = null;
                Integer duration = null;
                if (t.getServiceId() != null) {
                    Service svc = serviceCache.computeIfAbsent(t.getServiceId(),
                            id -> serviceRepository.findById(id).orElse(null));
                    if (svc != null) { code = svc.getCode(); name = svc.getName(); duration = svc.getDurationMinutes(); }
                }
                tierResponses.add(new PackageTierResponse(
                        t.getId(), t.getServiceId(), code, name,
                        t.getWeekdayPrice(), t.getWeekendPrice(), duration
                ));
            }
            responses.add(new PackageResponse(
                    pkg.getId(), pkg.getCode(), pkg.getName(), pkg.getDescription(), pkg.getBenefits(),
                    pkg.getMaxStayHours(), pkg.getDefaultPax(), pkg.isCouple(),
                    pkg.isIncludesMassage(), pkg.isBundlesPrivateRoom(), pkg.isRequiresVipRoom(), pkg.isActive(),
                    packageService.deriveInclusions(pkg), tierResponses
            ));
        }
        return responses;
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<PackageResponse> listAll(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return packageService.listAll(UUID.fromString(principal.organizationId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public PackageResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @Valid @RequestBody PackageRequest request) {
        return packageService.create(UUID.fromString(principal.organizationId()), request);
    }

    @PatchMapping("/{packageId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public PackageResponse update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable Long packageId,
                                  @Valid @RequestBody PackageRequest request) {
        return packageService.update(UUID.fromString(principal.organizationId()), packageId, request);
    }

    @DeleteMapping("/{packageId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                           @PathVariable Long packageId) {
        packageService.deactivate(UUID.fromString(principal.organizationId()), packageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{packageId}/reactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public PackageResponse reactivate(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @PathVariable Long packageId) {
        return packageService.reactivate(UUID.fromString(principal.organizationId()), packageId);
    }
}
