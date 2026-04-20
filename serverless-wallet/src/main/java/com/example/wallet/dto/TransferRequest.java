package com.example.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank String transactionId,
        @NotBlank String fromAccountId,
        @NotBlank String toAccountId,
        @NotNull @Positive BigDecimal amount) {
}
