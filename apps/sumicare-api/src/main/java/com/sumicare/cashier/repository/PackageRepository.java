package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.Package;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<Package, Long> {
    List<Package> findAllByOrganizationIdAndActiveTrueOrderByName(UUID organizationId);
    List<Package> findAllByOrganizationIdOrderByActiveDescNameAsc(UUID organizationId);
    Optional<Package> findByOrganizationIdAndCode(UUID organizationId, String code);
}
