package com.sumicare.therapist.repository;

import com.sumicare.therapist.domain.Therapist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TherapistRepository extends JpaRepository<Therapist, UUID> {
    List<Therapist> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
    Optional<Therapist> findByOrganizationIdAndStaffNumber(UUID organizationId, String staffNumber);
}
