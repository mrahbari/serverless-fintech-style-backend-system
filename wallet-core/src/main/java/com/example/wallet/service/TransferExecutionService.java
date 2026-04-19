package com.example.wallet.service;

import com.example.wallet.domain.TransferResult;

import java.math.BigDecimal;

public interface TransferExecutionService {

    TransferResult executeTransfer(String transactionId, String fromAccountId, String toAccountId, BigDecimal amount);
}
