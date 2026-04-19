package com.example.wallet.domain;

import java.math.BigDecimal;

public record TransferResult(
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        boolean idempotentReplay) {}
