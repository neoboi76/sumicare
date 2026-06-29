/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.transaction.service;

import com.sumicare.transaction.domain.TherapistTip;
import com.sumicare.transaction.repository.TherapistTipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class TipService {

    public static final String SOURCE_CASHIER = "CASHIER";
    public static final String SOURCE_SURVEY = "CLIENT_SURVEY";

    private final TherapistTipRepository tipRepository;

    public TipService(TherapistTipRepository tipRepository) {
        this.tipRepository = tipRepository;
    }

    @Transactional
    public TherapistTip recordTip(UUID organizationId, UUID therapistId, UUID orderId, UUID sessionId,
                                  BigDecimal amount, String source, UUID recordedByUserId) {
        if (therapistId == null) {
            throw new IllegalArgumentException("A therapist is required to record a tip");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tip amount must be greater than zero");
        }
        TherapistTip tip = new TherapistTip();
        tip.setOrganizationId(organizationId);
        tip.setTherapistId(therapistId);
        tip.setOrderId(orderId);
        tip.setSessionId(sessionId);
        tip.setAmount(amount);
        tip.setSource(source);
        tip.setRecordedByUserId(recordedByUserId);
        tip.setRecordedAt(OffsetDateTime.now());
        return tipRepository.save(tip);
    }
}
