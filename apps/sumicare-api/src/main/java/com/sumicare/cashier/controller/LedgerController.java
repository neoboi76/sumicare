/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.domain.LedgerAccount;
import com.sumicare.cashier.domain.Order;
import com.sumicare.cashier.repository.LedgerAccountRepository;
import com.sumicare.cashier.repository.OrderRepository;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cashier/ledger")
public class LedgerController {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private static final List<String> LEDGER_TYPES = List.of("CASH_BOOK", "DEBT", "REFERRAL");

    private final TransactionLedgerRepository ledgerRepository;
    private final PosTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final LedgerAccountRepository ledgerAccountRepository;

    public LedgerController(TransactionLedgerRepository ledgerRepository,
                            PosTransactionRepository transactionRepository,
                            OrderRepository orderRepository,
                            LedgerAccountRepository ledgerAccountRepository) {
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    public record LedgerAccountResponse(UUID id, String name, String shortName, String type, OffsetDateTime createdAt) {}

    public record CreateLedgerAccountRequest(String name, String shortName, String type) {}

    @GetMapping("/accounts")
    public List<LedgerAccountResponse> listAccounts(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ledgerAccountRepository.findAllByOrganizationIdOrderByCreatedAtDesc(
                        UUID.fromString(principal.organizationId())).stream()
                .map(a -> new LedgerAccountResponse(a.getId(), a.getName(), a.getShortName(), a.getType(), a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public LedgerAccountResponse createAccount(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @RequestBody CreateLedgerAccountRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Ledger name is required");
        }
        if (request.shortName() == null || request.shortName().isBlank()) {
            throw new IllegalArgumentException("Short name is required");
        }
        String type = request.type() == null ? "" : request.type().toUpperCase();
        if (!LEDGER_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported ledger type: " + request.type());
        }
        LedgerAccount account = new LedgerAccount();
        account.setOrganizationId(UUID.fromString(principal.organizationId()));
        account.setName(request.name().trim());
        account.setShortName(request.shortName().trim());
        account.setType(type);
        LedgerAccount saved = ledgerAccountRepository.save(account);
        return new LedgerAccountResponse(saved.getId(), saved.getName(), saved.getShortName(), saved.getType(), saved.getCreatedAt());
    }

    public record LedgerEntryResponse(
            Long id,
            UUID transactionId,
            UUID orderId,
            String orNumber,
            String clientNickname,
            String entryType,
            String paymentMethod,
            BigDecimal amount,
            OffsetDateTime recordedAt,
            String metadata,
            String status
    ) {}

    public record BalanceResponse(BigDecimal inflow, BigDecimal outflow, BigDecimal balance, int count) {}

    public record DailyRevenuePoint(LocalDate date, BigDecimal net, BigDecimal inflow, BigDecimal outflow, long count) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<LedgerEntryResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @RequestParam(required = false) String method,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        DateRange range = resolveRange(from, to);
        return enrich(loadEntries(orgId, method, range.start(), range.end()));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public BalanceResponse balance(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestParam(required = false) String method,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        DateRange range = resolveRange(from, to);
        List<TransactionLedgerEntry> entries = loadEntries(orgId, method, range.start(), range.end());
        BigDecimal inflow = BigDecimal.ZERO;
        BigDecimal outflow = BigDecimal.ZERO;
        for (TransactionLedgerEntry e : entries) {
            if ("REFUND".equalsIgnoreCase(e.getEntryType()) || "PAYOUT".equalsIgnoreCase(e.getEntryType())) {
                outflow = outflow.add(e.getAmount());
            } else {
                inflow = inflow.add(e.getAmount());
            }
        }
        return new BalanceResponse(inflow, outflow, inflow.subtract(outflow), entries.size());
    }

    @GetMapping("/daily-revenue")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<DailyRevenuePoint> dailyRevenue(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                @RequestParam(required = false) String method,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        LocalDate startDate = from != null ? from : LocalDate.now(MANILA).minusDays(13);
        LocalDate endDate = to != null ? to : LocalDate.now(MANILA);
        OffsetDateTime start = startDate.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        List<TransactionLedgerEntry> entries = loadEntries(orgId, method, start, end);

        Map<LocalDate, BigDecimal> inflowByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> outflowByDate = new HashMap<>();
        Map<LocalDate, Long> countByDate = new HashMap<>();
        for (TransactionLedgerEntry e : entries) {
            LocalDate date = e.getRecordedAt().atZoneSameInstant(MANILA).toLocalDate();
            boolean outflow = "REFUND".equalsIgnoreCase(e.getEntryType()) || "PAYOUT".equalsIgnoreCase(e.getEntryType());
            if (outflow) {
                outflowByDate.merge(date, e.getAmount(), BigDecimal::add);
            } else {
                inflowByDate.merge(date, e.getAmount(), BigDecimal::add);
            }
            countByDate.merge(date, 1L, Long::sum);
        }

        List<DailyRevenuePoint> points = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            BigDecimal inflow = inflowByDate.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal outflow = outflowByDate.getOrDefault(d, BigDecimal.ZERO);
            points.add(new DailyRevenuePoint(d, inflow.subtract(outflow), inflow, outflow, countByDate.getOrDefault(d, 0L)));
        }
        return points;
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam(required = false) String method,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        DateRange range = resolveRange(from, to);
        List<LedgerEntryResponse> rows = enrich(loadEntries(orgId, method, range.start(), range.end()));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("Recorded At,OR#,Client,Method,Type,Amount,Notes\n");
        for (LedgerEntryResponse r : rows) {
            sb.append(csvCell(r.recordedAt() != null ? r.recordedAt().atZoneSameInstant(MANILA).format(fmt) : "")).append(',')
              .append(csvCell(r.orNumber())).append(',')
              .append(csvCell(r.clientNickname())).append(',')
              .append(csvCell(r.paymentMethod())).append(',')
              .append(csvCell(r.entryType())).append(',')
              .append(r.amount() != null ? r.amount().toPlainString() : "0").append(',')
              .append(csvCell(r.metadata()))
              .append('\n');
        }
        String label = method == null || method.isBlank() ? "all" : method.toLowerCase();
        LocalDate startDate = from != null ? from : LocalDate.now(MANILA);
        LocalDate endDate = to != null ? to : startDate;
        String filename = "ledger-" + label + "-" + startDate + "-to-" + endDate + ".csv";
        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }

    private record DateRange(OffsetDateTime start, OffsetDateTime end) {}

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate startDate = from != null ? from : LocalDate.now(MANILA);
        LocalDate endDate = to != null ? to : startDate;
        OffsetDateTime start = startDate.atStartOfDay(MANILA).toOffsetDateTime();
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay(MANILA).toOffsetDateTime();
        return new DateRange(start, end);
    }

    private List<TransactionLedgerEntry> loadEntries(UUID orgId, String method, OffsetDateTime start, OffsetDateTime end) {
        return (method != null && !method.isBlank() && !"ALL".equalsIgnoreCase(method))
                ? ledgerRepository.findAllByOrganizationIdAndPaymentMethodAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, method, start, end)
                : ledgerRepository.findAllByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, start, end);
    }

    private List<LedgerEntryResponse> enrich(List<TransactionLedgerEntry> entries) {
        if (entries.isEmpty()) return List.of();
        Map<UUID, PosTransaction> txById = transactionRepository.findAllById(
                entries.stream().map(TransactionLedgerEntry::getTransactionId).toList()
        ).stream().collect(Collectors.toMap(PosTransaction::getId, t -> t, (a, b) -> a));

        return entries.stream().map(e -> {
            PosTransaction tx = txById.get(e.getTransactionId());
            String orNumber = tx != null ? tx.getReceiptNumber() : null;
            String method = e.getPaymentMethod();
            if (method == null && tx != null) method = tx.getPaymentMethod();
            UUID orderId = parseOrderIdFromMetadata(e.getMetadata());
            String nickname = null;
            if (orderId != null) {
                var orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isPresent()) {
                    Order o = orderOpt.get();
                    if (o.getOrNumber() != null) orNumber = o.getOrNumber();
                }
            }
            return new LedgerEntryResponse(
                    e.getId(),
                    e.getTransactionId(),
                    orderId,
                    orNumber,
                    nickname,
                    e.getEntryType(),
                    method,
                    e.getAmount(),
                    e.getRecordedAt(),
                    e.getMetadata(),
                    e.getStatus()
            );
        }).toList();
    }

    private UUID parseOrderIdFromMetadata(String metadata) {
        if (metadata == null) return null;
        int idx = metadata.indexOf("\"orderId\":\"");
        if (idx < 0) return null;
        int start = idx + "\"orderId\":\"".length();
        int end = metadata.indexOf('"', start);
        if (end < 0) return null;
        try {
            return UUID.fromString(metadata.substring(start, end));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String csvCell(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
