package com.sumicare.booking.dto;

public record PublicPaymentResponse(String status, String intentId, String redirectUrl, String orNumber) {}
