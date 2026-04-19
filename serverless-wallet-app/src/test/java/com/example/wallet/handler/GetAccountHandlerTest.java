package com.example.wallet.handler;

import com.example.wallet.domain.Account;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.view.AccountSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountHandlerTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private TransferExecutionService transferExecution;

    private GetAccountHandler handler;

    @BeforeEach
    void setUp() {
        var wallet = new WalletFacade(accounts, eventPublisher, transferExecution);
        handler = new GetAccountHandler(wallet);
    }

    @Test
    void returnsSummaryWhenFound() {
        when(accounts.findById("x")).thenReturn(Optional.of(new Account("x", BigDecimal.TEN)));

        AccountSummary s = handler.handle("x");

        assertThat(s.accountId()).isEqualTo("x");
        assertThat(s.balance()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void throwsWhenMissing() {
        when(accounts.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle("missing")).isInstanceOf(AccountNotFoundException.class);
    }
}
