package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.PackageTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageTierRepository extends JpaRepository<PackageTier, Long> {
    List<PackageTier> findAllByPackageId(Long packageId);
}
