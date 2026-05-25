package com.sumicare.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaResendRequest(@NotBlank String challengeId) {}
