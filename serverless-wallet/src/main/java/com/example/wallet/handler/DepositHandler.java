package com.example.wallet.handler;

import com.example.wallet.facade.WalletFacade;
import com.example.wallet.view.DepositResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DepositHandler {

    private final WalletFacade wallet;

    public DepositHandler(WalletFacade wallet) {
        this.wallet = wallet;
    }

    public DepositResult handle(String accountId, BigDecimal amount) {
        return wallet.deposit(accountId, amount);
    }
}
