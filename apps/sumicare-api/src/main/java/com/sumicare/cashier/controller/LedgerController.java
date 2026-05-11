package com.sumicare.cashier.controller;

import com.sumicare.auth.filter.JwtAuthenticationFilter.AuthenticatedPrincipal;
import com.sumicare.cashier.domain.Order;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cashier/ledger")
public class LedgerController {

    private final TransactionLedgerRepository ledgerRepository;
    private final PosTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;

    public LedgerController(TransactionLedgerRepository ledgerRepository,
                            PosTransactionRepository transactionRepository,
                            OrderRepository orderRepository) {
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
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
            String metadata
    ) {}

    public record BalanceResponse(BigDecimal inflow, BigDecimal outflow, BigDecimal balance, int count) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<LedgerEntryResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @RequestParam(required = false) String method,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null ? from : OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end = to != null ? to : start.plusDays(1);
        List<TransactionLedgerEntry> entries = (method != null && !method.isBlank() && !"ALL".equalsIgnoreCase(method))
                ? ledgerRepository.findAllByOrganizationIdAndPaymentMethodAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, method, start, end)
                : ledgerRepository.findAllByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, start, end);
        return enrich(entries);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public BalanceResponse balance(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @RequestParam(required = false) String method,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null ? from : OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end = to != null ? to : start.plusDays(1);
        List<TransactionLedgerEntry> entries = (method != null && !method.isBlank() && !"ALL".equalsIgnoreCase(method))
                ? ledgerRepository.findAllByOrganizationIdAndPaymentMethodAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, method, start, end)
                : ledgerRepository.findAllByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, start, end);
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

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam(required = false) String method,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID orgId = UUID.fromString(principal.organizationId());
        OffsetDateTime start = from != null ? from : OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end = to != null ? to : start.plusDays(1);
        List<TransactionLedgerEntry> entries = (method != null && !method.isBlank() && !"ALL".equalsIgnoreCase(method))
                ? ledgerRepository.findAllByOrganizationIdAndPaymentMethodAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, method, start, end)
                : ledgerRepository.findAllByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtDesc(orgId, start, end);
        List<LedgerEntryResponse> rows = enrich(entries);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("Recorded At,OR#,Client,Method,Type,Amount,Notes\n");
        for (LedgerEntryResponse r : rows) {
            sb.append(csvCell(r.recordedAt() != null ? r.recordedAt().format(fmt) : "")).append(',')
              .append(csvCell(r.orNumber())).append(',')
              .append(csvCell(r.clientNickname())).append(',')
              .append(csvCell(r.paymentMethod())).append(',')
              .append(csvCell(r.entryType())).append(',')
              .append(r.amount() != null ? r.amount().toPlainString() : "0").append(',')
              .append(csvCell(r.metadata()))
              .append('\n');
        }
        String label = method == null || method.isBlank() ? "all" : method.toLowerCase();
        String filename = "ledger-" + label + "-" + start.toLocalDate() + "-to-" + end.toLocalDate() + ".csv";
        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
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
                    e.getMetadata()
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
