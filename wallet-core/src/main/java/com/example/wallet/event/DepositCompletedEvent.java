package com.example.wallet.event;

import java.math.BigDecimal;
import java.time.Instant;

public record DepositCompletedEvent(String accountId, BigDecimal amount, BigDecimal newBalance, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "DEPOSIT_COMPLETED";
    }
}
