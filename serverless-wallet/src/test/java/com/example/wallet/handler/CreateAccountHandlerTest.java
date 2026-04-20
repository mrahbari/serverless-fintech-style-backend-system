package com.example.wallet.handler;

import com.example.wallet.event.AccountCreatedEvent;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import com.example.wallet.view.CreateAccountResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAccountHandlerTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private TransferExecutionService transferExecution;

    private CreateAccountHandler handler;

    @BeforeEach
    void setUp() {
        var wallet = new WalletFacade(accounts, eventPublisher, transferExecution);
        handler = new CreateAccountHandler(wallet);
    }

    @Test
    void createsAccountAndPublishesEvent() {
        var account = com.example.wallet.domain.Account.newAccount(BigDecimal.TEN);
        when(accounts.createWithInitialBalance(BigDecimal.TEN)).thenReturn(account);

        CreateAccountResult result = handler.handle(BigDecimal.TEN);

        assertThat(result.accountId()).isEqualTo(account.getId());
        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.TEN);

        ArgumentCaptor<AccountCreatedEvent> captor = ArgumentCaptor.forClass(AccountCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo(account.getId());
        assertThat(captor.getValue().initialBalance()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void defaultBalanceZero() {
        var account = com.example.wallet.domain.Account.newAccount(BigDecimal.ZERO);
        when(accounts.createWithInitialBalance(eq(BigDecimal.ZERO))).thenReturn(account);

        CreateAccountResult result = handler.handle(null);

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
