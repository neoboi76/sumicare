package com.sumicare.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTreatmentSlipRequest(
        @Size(max = 16) String lockerNumber,
        @Size(max = 64) String roomNumber,
        @Size(max = 500) String othersAddOn,
        @Size(max = 1000) String remarks,
        @Size(max = 64) String orNumber,
        @Size(max = 64) String addOnOrNumber,
        @DecimalMin("0.00") BigDecimal totalAmount,
        @Min(0) @Max(120) Integer jacuzziMinutes,
        @Min(0) @Max(120) Integer massageMinutes,
        Boolean wineIncluded,
        Boolean waiverAccepted
) {}
