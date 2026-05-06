package com.sumicare.shift.repository;

import com.sumicare.shift.domain.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findAllByShiftId(Long shiftId);

    List<ShiftAssignment> findAllByTherapistId(UUID therapistId);

    boolean existsByShiftIdAndTherapistId(Long shiftId, UUID therapistId);

    @Modifying
    @Query("DELETE FROM ShiftAssignment sa WHERE sa.shiftId = :shiftId AND sa.therapistId = :therapistId")
    void deleteByShiftIdAndTherapistId(Long shiftId, UUID therapistId);
}
