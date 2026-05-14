package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.domain.PackageTier;
import com.sumicare.cashier.dto.PackageResponse;
import com.sumicare.cashier.dto.PackageTierResponse;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.cashier.repository.PackageTierRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public PackageController(PackageRepository packageRepository,
                             PackageTierRepository tierRepository,
                             ServiceRepository serviceRepository) {
        this.packageRepository = packageRepository;
        this.tierRepository = tierRepository;
        this.serviceRepository = serviceRepository;
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
                if (t.getServiceId() != null) {
                    Service svc = serviceCache.computeIfAbsent(t.getServiceId(),
                            id -> serviceRepository.findById(id).orElse(null));
                    if (svc != null) { code = svc.getCode(); name = svc.getName(); }
                }
                tierResponses.add(new PackageTierResponse(
                        t.getId(), t.getServiceId(), code, name,
                        t.getWeekdayPrice(), t.getWeekendPrice()
                ));
            }
            responses.add(new PackageResponse(
                    pkg.getId(), pkg.getCode(), pkg.getName(), pkg.getDescription(), pkg.getBenefits(),
                    pkg.getMaxStayHours(), pkg.getDefaultPax(), pkg.isCouple(),
                    pkg.isIncludesMassage(), pkg.isBundlesPrivateRoom(), pkg.isRequiresVipRoom(), pkg.isActive(),
                    tierResponses
            ));
        }
        return responses;
    }
}
