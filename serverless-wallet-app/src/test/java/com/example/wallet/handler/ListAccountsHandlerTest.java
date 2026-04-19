package com.example.wallet.handler;

import com.example.wallet.domain.Account;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.AccountPage;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import com.example.wallet.view.AccountListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAccountsHandlerTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private TransferExecutionService transferExecution;

    private ListAccountsHandler handler;

    @BeforeEach
    void setUp() {
        var wallet = new WalletFacade(accounts, eventPublisher, transferExecution);
        handler = new ListAccountsHandler(wallet);
    }

    @Test
    void listUsesSingleRepositoryPageNotPerRowFetch() {
        Account a = new Account("a-id", BigDecimal.ONE);
        when(accounts.findAccountsAfterCursor(null, 20)).thenReturn(new AccountPage(List.of(a), null));

        AccountListResponse r = handler.handle(null, null);

        assertThat(r.accounts()).hasSize(1);
        assertThat(r.accounts().get(0).accountId()).isEqualTo("a-id");
        assertThat(r.nextCursor()).isNull();
        verify(accounts).findAccountsAfterCursor(null, 20);
    }
}
