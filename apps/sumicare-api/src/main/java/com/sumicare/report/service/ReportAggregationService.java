package com.sumicare.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.report.domain.DayReport;
import com.sumicare.report.domain.MonthlyReport;
import com.sumicare.report.repository.DayReportRepository;
import com.sumicare.report.repository.MonthlyReportRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportAggregationService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final ReportService reportService;
    private final DayReportRepository dayReportRepository;
    private final MonthlyReportRepository monthlyReportRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public ReportAggregationService(ReportService reportService,
                                    DayReportRepository dayReportRepository,
                                    MonthlyReportRepository monthlyReportRepository,
                                    OrganizationRepository organizationRepository,
                                    ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.dayReportRepository = dayReportRepository;
        this.monthlyReportRepository = monthlyReportRepository;
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 5 6 * * *")
    @Transactional
    public void generateDailyReports() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        organizationRepository.findAll().forEach(org -> generateDayReport(org.getId(), yesterday));
    }

    @Scheduled(cron = "0 30 6 1 * *")
    @Transactional
    public void generateMonthlyReports() {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        organizationRepository.findAll().forEach(org -> generateMonthlyReport(org.getId(), previousMonth));
    }

    @Transactional
    public DayReport generateDayReport(UUID organizationId, LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        ReportService.ReportSummary summary = reportService.buildCutoffReport(organizationId, from, to);
        DayReport report = dayReportRepository.findByOrganizationIdAndReportDate(organizationId, date)
                .orElseGet(() -> {
                    DayReport r = new DayReport();
                    r.setOrganizationId(organizationId);
                    r.setReportDate(date);
                    return r;
                });
        report.setPayload(serialize(summary));
        report.setGeneratedAt(OffsetDateTime.now());
        return dayReportRepository.save(report);
    }

    @Transactional
    public MonthlyReport generateMonthlyReport(UUID organizationId, YearMonth ym) {
        OffsetDateTime from = ym.atDay(1).atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay(MANILA).toOffsetDateTime();
        ReportService.ReportSummary summary = reportService.buildCutoffReport(organizationId, from, to);
        List<DayReport> days = dayReportRepository
                .findAllByOrganizationIdAndReportDateBetweenOrderByReportDateDesc(organizationId, ym.atDay(1), ym.atEndOfMonth());
        Map<String, Object> rolled = new HashMap<>();
        rolled.put("summary", summary);
        rolled.put("dayCount", days.size());
        MonthlyReport report = monthlyReportRepository
                .findByOrganizationIdAndReportYearAndReportMonth(organizationId, ym.getYear(), ym.getMonthValue())
                .orElseGet(() -> {
                    MonthlyReport r = new MonthlyReport();
                    r.setOrganizationId(organizationId);
                    r.setReportYear(ym.getYear());
                    r.setReportMonth(ym.getMonthValue());
                    return r;
                });
        report.setPayload(serialize(rolled));
        report.setGeneratedAt(OffsetDateTime.now());
        return monthlyReportRepository.save(report);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
