package com.example.wallet.handler;

import com.example.wallet.facade.WalletFacade;
import com.example.wallet.view.AccountListResponse;
import org.springframework.stereotype.Component;

@Component
public class ListAccountsHandler {

    private final WalletFacade wallet;

    public ListAccountsHandler(WalletFacade wallet) {
        this.wallet = wallet;
    }

    public AccountListResponse handle(Integer limit, String cursor) {
        return wallet.listAccounts(limit, cursor);
    }
}
