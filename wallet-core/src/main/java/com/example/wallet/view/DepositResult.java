package com.example.wallet.view;

import java.math.BigDecimal;

public record DepositResult(String accountId, BigDecimal newBalance) {}
