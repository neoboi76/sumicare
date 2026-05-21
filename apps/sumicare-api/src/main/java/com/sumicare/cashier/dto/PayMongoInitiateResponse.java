package com.sumicare.cashier.dto;

public record PayMongoInitiateResponse(String status, String intentId, String redirectUrl) {}
