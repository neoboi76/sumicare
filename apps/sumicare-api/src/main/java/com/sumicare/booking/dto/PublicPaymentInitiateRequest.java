package com.sumicare.booking.dto;

import com.sumicare.cashier.dto.PaymentDetailsRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PublicPaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotBlank String paymentMethod,
        PaymentDetailsRequest paymentDetails,
        @Size(max = 300) String returnPath
) {}
