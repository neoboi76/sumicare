package com.sumicare.shift.service;

import com.sumicare.shift.domain.Shift;
import com.sumicare.shift.repository.ShiftRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<Shift> listActive(UUID organizationId) {
        return shiftRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
    }

    public Optional<Shift> resolveActiveShiftFor(UUID organizationId, LocalTime now) {
        return listActive(organizationId).stream()
                .filter(s -> coversTime(s.getStartTime(), s.getEndTime(), now))
                .max((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
    }

    public boolean coversTime(LocalTime start, LocalTime end, LocalTime now) {
        if (end.isAfter(start)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
