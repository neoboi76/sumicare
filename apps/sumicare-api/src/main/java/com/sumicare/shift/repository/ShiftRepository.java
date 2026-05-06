package com.sumicare.shift.repository;

import com.sumicare.shift.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
}
