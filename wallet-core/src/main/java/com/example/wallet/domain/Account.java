package com.example.wallet.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-table style document: partition key {@code id}, attribute {@code balance}.
 * Maps to a DynamoDB item: PK = ACCOUNT#&lt;id&gt;, balance as Number.
 */
public final class Account {

    private final String id;
    private BigDecimal balance;

    public Account(String id, BigDecimal balance) {
        this.id = Objects.requireNonNull(id);
        this.balance = balance != null ? balance : BigDecimal.ZERO;
    }

    public static Account newAccount(BigDecimal initialBalance) {
        return new Account(UUID.randomUUID().toString(), initialBalance);
    }

    public String getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
