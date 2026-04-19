package com.example.wallet.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds on account " + accountId + ": requested " + requested + ", available " + available);
    }
}
