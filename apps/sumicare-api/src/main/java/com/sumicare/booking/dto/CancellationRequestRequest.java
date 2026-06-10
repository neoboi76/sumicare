package com.sumicare.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancellationRequestRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotBlank @Email @Size(max = 255) String email
) {}
