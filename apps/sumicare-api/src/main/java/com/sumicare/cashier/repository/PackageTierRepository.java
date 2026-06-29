/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.PackageTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PackageTierRepository extends JpaRepository<PackageTier, Long> {
    List<PackageTier> findAllByPackageId(Long packageId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PackageTier t WHERE t.packageId = :packageId")
    void deleteAllByPackageId(@Param("packageId") Long packageId);
}
