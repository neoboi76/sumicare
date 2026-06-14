package com.sumicare.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemInvitationRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
