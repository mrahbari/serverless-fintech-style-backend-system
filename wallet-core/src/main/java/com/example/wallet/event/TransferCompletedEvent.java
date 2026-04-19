package com.example.wallet.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferCompletedEvent(
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "TRANSFER_COMPLETED";
    }
}
