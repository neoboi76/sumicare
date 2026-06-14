package com.sumicare.booking.dto;

public record PublicPaymentResponse(
        String status,
        String intentId,
        String redirectUrl,
        String orNumber,
        String reference,
        String clientNickname,
        String packageName,
        String serviceName,
        String scheduledAt,
        String reservationType) {}
