package com.example.wallet.config;

import com.example.wallet.event.EventPublisher;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletFacadeConfiguration {

    @Bean
    public WalletFacade walletFacade(
            AccountRepository accounts,
            EventPublisher eventPublisher,
            TransferExecutionService transferExecution) {
        return new WalletFacade(accounts, eventPublisher, transferExecution);
    }
}
