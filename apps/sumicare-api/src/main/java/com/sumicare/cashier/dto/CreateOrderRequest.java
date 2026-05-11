package com.sumicare.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID clientId,
        @Size(max = 64) String clientNickname,
        @Size(max = 16) String lockerNumber,
        @Size(max = 1) String clientGender,
        Integer pax,
        @NotNull List<Long> serviceIds,
        UUID primaryTherapistId,
        UUID secondaryTherapistId,
        UUID roomId,
        UUID bedId,
        @Size(max = 100) String referenceNumber,
        @Size(max = 1000) String notes,
        @Size(max = 50) String orNumber,
        @DecimalMin("0.00") BigDecimal subtotal,
        @DecimalMin("0.00") BigDecimal discount,
        @DecimalMin("0.00") BigDecimal total,
        InitialPayment initialPayment
) {
    public record InitialPayment(
            @NotNull String paymentMethod,
            @DecimalMin("0.00") @NotNull BigDecimal amount,
            @Size(max = 100) String referenceNumber
    ) {}
}
