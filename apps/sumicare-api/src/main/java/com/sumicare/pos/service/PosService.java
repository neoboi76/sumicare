package com.sumicare.pos.service;

import com.sumicare.booking.domain.Booking;
import com.sumicare.booking.domain.Session;
import com.sumicare.booking.repository.BookingRepository;
import com.sumicare.booking.repository.SessionRepository;
import com.sumicare.pos.domain.CashierShift;
import com.sumicare.pos.domain.PosTransaction;
import com.sumicare.pos.domain.TransactionLedgerEntry;
import com.sumicare.pos.dto.PaymentResponse;
import com.sumicare.pos.dto.ProcessPaymentRequest;
import com.sumicare.pos.repository.CashierShiftRepository;
import com.sumicare.pos.repository.PosTransactionRepository;
import com.sumicare.pos.repository.TransactionLedgerRepository;
import com.sumicare.service_catalogue.repository.ServiceRepository;
import com.sumicare.transaction.domain.Commission;
import com.sumicare.transaction.repository.CommissionRepository;
import com.sumicare.voucher.domain.Voucher;
import com.sumicare.voucher.service.VoucherService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PosService {

    private final PosTransactionRepository transactionRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final SessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final CommissionRepository commissionRepository;
    private final VoucherService voucherService;

    public PosService(PosTransactionRepository transactionRepository,
                      TransactionLedgerRepository ledgerRepository,
                      CashierShiftRepository cashierShiftRepository,
                      SessionRepository sessionRepository,
                      BookingRepository bookingRepository,
                      ServiceRepository serviceRepository,
                      CommissionRepository commissionRepository,
                      VoucherService voucherService) {
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.cashierShiftRepository = cashierShiftRepository;
        this.sessionRepository = sessionRepository;
        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
        this.commissionRepository = commissionRepository;
        this.voucherService = voucherService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public PaymentResponse processPayment(UUID organizationId, UUID processedBy, ProcessPaymentRequest request) {
        Session session = sessionRepository.findById(request.sessionId()).orElseThrow();
        BigDecimal subtotal = request.subtotal();
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        Voucher redeemedVoucher = null;

        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            redeemedVoucher = voucherService.findValid(organizationId, request.voucherCode())
                    .orElseThrow(() -> new IllegalArgumentException("Voucher invalid or already redeemed"));
            BigDecimal voucherDiscount = voucherService.computeDiscount(redeemedVoucher, subtotal);
            discount = discount.add(voucherDiscount);
        }
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        PosTransaction tx = new PosTransaction();
        tx.setOrganizationId(organizationId);
        tx.setSessionId(session.getId());
        tx.setReceiptNumber(generateReceiptNumber());
        tx.setSubtotal(subtotal);
        tx.setDiscount(discount);
        tx.setTotal(total);
        tx.setPaymentMethod(request.paymentMethod());
        tx.setProcessedBy(processedBy);
        tx.setProcessedAt(OffsetDateTime.now());
        tx.setStatus("COMPLETED");
        transactionRepository.save(tx);

        TransactionLedgerEntry entry = new TransactionLedgerEntry();
        entry.setOrganizationId(organizationId);
        entry.setTransactionId(tx.getId());
        entry.setEntryType("PAYMENT_RECEIVED");
        entry.setAmount(total);
        entry.setPaymentMethod(request.paymentMethod());
        ledgerRepository.save(entry);

        if (redeemedVoucher != null) {
            voucherService.markRedeemed(redeemedVoucher.getId(),
                    bookingRepository.findById(session.getBookingId())
                            .map(Booking::getClientId).orElse(null));
        }

        recordCommissionsForSession(organizationId, session);
        return new PaymentResponse(tx.getId(), tx.getReceiptNumber(),
                tx.getSubtotal(), tx.getDiscount(), tx.getTotal(),
                tx.getPaymentMethod(), tx.getProcessedAt());
    }

    @Transactional
    public void recordCommissionsForSession(UUID organizationId, Session session) {
        if (session.getPrimaryTherapistId() == null) return;
        if (commissionRepository.existsBySessionIdAndTherapistId(session.getId(), session.getPrimaryTherapistId())) return;
        Booking booking = bookingRepository.findById(session.getBookingId()).orElse(null);
        if (booking == null) return;
        var service = serviceRepository.findById(booking.getServiceId()).orElse(null);
        BigDecimal base = service == null ? BigDecimal.ZERO : service.getCommissionAmount();
        boolean tandem = service != null && service.isRequiresTwoTherapists();
        BigDecimal primaryShare = tandem
                ? base.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                : base;
        Commission primaryComm = new Commission();
        primaryComm.setOrganizationId(organizationId);
        primaryComm.setSessionId(session.getId());
        primaryComm.setTherapistId(session.getPrimaryTherapistId());
        primaryComm.setAmount(primaryShare);
        commissionRepository.save(primaryComm);

        if (session.getSecondaryTherapistId() != null && tandem) {
            Commission secondaryComm = new Commission();
            secondaryComm.setOrganizationId(organizationId);
            secondaryComm.setSessionId(session.getId());
            secondaryComm.setTherapistId(session.getSecondaryTherapistId());
            secondaryComm.setAmount(primaryShare);
            commissionRepository.save(secondaryComm);
        }

        if (session.isExtension() && session.getExtensionMinutes() > 0) {
            BigDecimal halfHour = BigDecimal.valueOf(60);
            int halves = (int) Math.ceil(session.getExtensionMinutes() / 30.0);
            BigDecimal extra = halfHour.multiply(BigDecimal.valueOf(halves));
            Commission extComm = new Commission();
            extComm.setOrganizationId(organizationId);
            extComm.setSessionId(session.getId());
            extComm.setTherapistId(session.getPrimaryTherapistId());
            extComm.setAmount(extra);
            extComm.setExtension(true);
            commissionRepository.save(extComm);
        }
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public CashierShift openCashierShift(UUID organizationId, UUID cashierUserId, BigDecimal openingFloat) {
        CashierShift shift = new CashierShift();
        shift.setOrganizationId(organizationId);
        shift.setCashierUserId(cashierUserId);
        shift.setOpeningFloat(openingFloat == null ? BigDecimal.ZERO : openingFloat);
        shift.setStatus("OPEN");
        return cashierShiftRepository.save(shift);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER','RECEPTIONIST')")
    @Transactional
    public CashierShift closeCashierShift(UUID cashierShiftId, BigDecimal closingTotal) {
        CashierShift shift = cashierShiftRepository.findById(cashierShiftId).orElseThrow();
        shift.setClosedAt(OffsetDateTime.now());
        shift.setClosingTotal(closingTotal);
        shift.setVariance(closingTotal == null ? null : closingTotal.subtract(shift.getOpeningFloat()));
        shift.setStatus("CLOSED");
        return shift;
    }

    private String generateReceiptNumber() {
        return "OR" + System.currentTimeMillis() % 1000000;
    }
}
