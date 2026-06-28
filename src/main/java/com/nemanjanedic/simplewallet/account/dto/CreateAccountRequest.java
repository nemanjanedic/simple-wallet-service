package com.nemanjanedic.simplewallet.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Initial balance must have at most 2 decimal places")
        BigDecimal initialBalance
) {
}
