package com.sumicare.report.repository;

import com.sumicare.report.domain.DayReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DayReportRepository extends JpaRepository<DayReport, UUID> {
    Optional<DayReport> findByOrganizationIdAndReportDate(UUID organizationId, LocalDate reportDate);
    List<DayReport> findAllByOrganizationIdAndReportDateBetweenOrderByReportDateDesc(UUID organizationId, LocalDate from, LocalDate to);
}
