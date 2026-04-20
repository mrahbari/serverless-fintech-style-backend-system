package com.example.wallet.handler;

import com.example.wallet.event.DepositCompletedEvent;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import com.example.wallet.view.DepositResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositHandlerTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private TransferExecutionService transferExecution;

    private DepositHandler handler;

    @BeforeEach
    void setUp() {
        var wallet = new WalletFacade(accounts, eventPublisher, transferExecution);
        handler = new DepositHandler(wallet);
    }

    @Test
    void depositsAndPublishesEvent() {
        var accountId = "acc-1";
        when(accounts.depositAdd(accountId, new BigDecimal("5.00"))).thenReturn(new BigDecimal("6.00"));

        DepositResult result = handler.handle(accountId, new BigDecimal("5.00"));

        assertThat(result.newBalance()).isEqualByComparingTo(new BigDecimal("6.00"));

        ArgumentCaptor<DepositCompletedEvent> captor = ArgumentCaptor.forClass(DepositCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo(accountId);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void missingAccountThrows() {
        when(accounts.depositAdd("missing", BigDecimal.ONE)).thenThrow(new AccountNotFoundException("missing"));

        assertThatThrownBy(() -> handler.handle("missing", BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
    }
}
