package com.example.wallet.handler;

import com.example.wallet.facade.WalletFacade;
import com.example.wallet.view.CreateAccountResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps to a single Lambda function: one handler class per use case, triggered by API Gateway.
 */
@Component
public class CreateAccountHandler {

    private final WalletFacade wallet;

    public CreateAccountHandler(WalletFacade wallet) {
        this.wallet = wallet;
    }

    public CreateAccountResult handle(BigDecimal initialBalance) {
        return wallet.createAccount(initialBalance);
    }
}
