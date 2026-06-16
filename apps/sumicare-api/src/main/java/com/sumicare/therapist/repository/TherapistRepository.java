/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.therapist.repository;

import com.sumicare.therapist.domain.Therapist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TherapistRepository extends JpaRepository<Therapist, UUID> {
    List<Therapist> findAllByOrganizationId(UUID organizationId);
    List<Therapist> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
    List<Therapist> findAllByOrganizationIdAndActiveFalse(UUID organizationId);
    Optional<Therapist> findByOrganizationIdAndStaffNumber(UUID organizationId, String staffNumber);
    boolean existsByOrganizationIdAndNickname(UUID organizationId, String nickname);
    boolean existsByOrganizationIdAndStaffNumber(UUID organizationId, String staffNumber);
}
