package com.example.wallet.event;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountCreatedEvent(String accountId, BigDecimal initialBalance, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String type() {
        return "ACCOUNT_CREATED";
    }
}
