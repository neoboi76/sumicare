package com.sumicare.report.repository;

import com.sumicare.report.domain.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, UUID> {
    Optional<MonthlyReport> findByOrganizationIdAndReportYearAndReportMonth(UUID organizationId, int reportYear, int reportMonth);
    List<MonthlyReport> findAllByOrganizationIdOrderByReportYearDescReportMonthDesc(UUID organizationId);
}
