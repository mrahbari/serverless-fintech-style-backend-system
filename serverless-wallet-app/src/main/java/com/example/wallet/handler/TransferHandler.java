package com.example.wallet.handler;

import com.example.wallet.domain.TransferResult;
import com.example.wallet.facade.WalletFacade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransferHandler {

    private final WalletFacade wallet;

    public TransferHandler(WalletFacade wallet) {
        this.wallet = wallet;
    }

    public TransferResult handle(String transactionId, String fromAccountId, String toAccountId, BigDecimal amount) {
        return wallet.transfer(transactionId, fromAccountId, toAccountId, amount);
    }
}
