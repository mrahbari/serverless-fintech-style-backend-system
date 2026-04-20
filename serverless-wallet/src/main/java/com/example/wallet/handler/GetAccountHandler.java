package com.example.wallet.handler;

import com.example.wallet.facade.WalletFacade;
import com.example.wallet.view.AccountSummary;
import org.springframework.stereotype.Component;

@Component
public class GetAccountHandler {

    private final WalletFacade wallet;

    public GetAccountHandler(WalletFacade wallet) {
        this.wallet = wallet;
    }

    public AccountSummary handle(String accountId) {
        return wallet.getAccount(accountId);
    }
}
