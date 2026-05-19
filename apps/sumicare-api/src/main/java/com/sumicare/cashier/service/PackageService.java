package com.sumicare.cashier.service;

import com.sumicare.cashier.domain.Package;
import com.sumicare.cashier.domain.PackageTier;
import com.sumicare.cashier.dto.PackageRequest;
import com.sumicare.cashier.dto.PackageResponse;
import com.sumicare.cashier.dto.PackageTierRequest;
import com.sumicare.cashier.dto.PackageTierResponse;
import com.sumicare.cashier.repository.PackageRepository;
import com.sumicare.cashier.repository.PackageTierRepository;
import com.sumicare.service_catalogue.domain.Service;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final PackageTierRepository tierRepository;
    private final ServiceRepository serviceRepository;

    public PackageService(PackageRepository packageRepository,
                          PackageTierRepository tierRepository,
                          ServiceRepository serviceRepository) {
        this.packageRepository = packageRepository;
        this.tierRepository = tierRepository;
        this.serviceRepository = serviceRepository;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<PackageResponse> listAll(UUID organizationId) {
        List<Package> packages = packageRepository.findAllByOrganizationIdOrderByActiveDescNameAsc(organizationId);
        Map<Long, Service> serviceCache = new HashMap<>();
        List<PackageResponse> out = new ArrayList<>();
        for (Package pkg : packages) {
            out.add(toResponse(pkg, serviceCache));
        }
        return out;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public PackageResponse create(UUID organizationId, PackageRequest request) {
        if (packageRepository.findByOrganizationIdAndCode(organizationId, request.code()).isPresent()) {
            throw new IllegalArgumentException("Package code already exists: " + request.code());
        }
        Package pkg = new Package();
        pkg.setOrganizationId(organizationId);
        applyRequest(pkg, request);
        packageRepository.save(pkg);
        if (request.tiers() != null) {
            persistTiers(pkg.getId(), request.tiers());
        }
        return toResponse(pkg, new HashMap<>());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public PackageResponse update(UUID organizationId, Long packageId, PackageRequest request) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown package"));
        if (!pkg.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Package not in organization");
        }
        if (!pkg.getCode().equals(request.code())) {
            packageRepository.findByOrganizationIdAndCode(organizationId, request.code()).ifPresent(other -> {
                if (!other.getId().equals(packageId)) {
                    throw new IllegalArgumentException("Package code already exists: " + request.code());
                }
            });
        }
        applyRequest(pkg, request);
        packageRepository.save(pkg);
        if (request.tiers() != null) {
            tierRepository.deleteAllByPackageId(pkg.getId());
            persistTiers(pkg.getId(), request.tiers());
        }
        return toResponse(pkg, new HashMap<>());
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public void deactivate(UUID organizationId, Long packageId) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown package"));
        if (!pkg.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Package not in organization");
        }
        pkg.setActive(false);
        packageRepository.save(pkg);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public PackageResponse reactivate(UUID organizationId, Long packageId) {
        Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown package"));
        if (!pkg.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Package not in organization");
        }
        pkg.setActive(true);
        packageRepository.save(pkg);
        return toResponse(pkg, new HashMap<>());
    }

    private void applyRequest(Package pkg, PackageRequest request) {
        pkg.setCode(request.code());
        pkg.setName(request.name());
        pkg.setDescription(request.description());
        pkg.setBenefits(request.benefits());
        pkg.setMaxStayHours(request.maxStayHours());
        pkg.setDefaultPax(request.defaultPax());
        if (request.couple() != null) pkg.setCouple(request.couple());
        if (request.includesMassage() != null) pkg.setIncludesMassage(request.includesMassage());
        if (request.bundlesPrivateRoom() != null) pkg.setBundlesPrivateRoom(request.bundlesPrivateRoom());
        if (request.requiresVipRoom() != null) pkg.setRequiresVipRoom(request.requiresVipRoom());
        if (request.active() != null) pkg.setActive(request.active());
    }

    private void persistTiers(Long packageId, List<PackageTierRequest> tiers) {
        for (PackageTierRequest tier : tiers) {
            PackageTier entity = new PackageTier();
            entity.setPackageId(packageId);
            entity.setServiceId(tier.serviceId());
            entity.setWeekdayPrice(tier.weekdayPrice());
            entity.setWeekendPrice(tier.weekendPrice());
            tierRepository.save(entity);
        }
    }

    private PackageResponse toResponse(Package pkg, Map<Long, Service> serviceCache) {
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
                    t.getWeekdayPrice(), t.getWeekendPrice()));
        }
        return new PackageResponse(
                pkg.getId(), pkg.getCode(), pkg.getName(), pkg.getDescription(), pkg.getBenefits(),
                pkg.getMaxStayHours(), pkg.getDefaultPax(), pkg.isCouple(),
                pkg.isIncludesMassage(), pkg.isBundlesPrivateRoom(), pkg.isRequiresVipRoom(), pkg.isActive(),
                tierResponses);
    }
}
