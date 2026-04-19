package com.example.wallet.handler;

import com.example.wallet.domain.TransferResult;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.event.TransferCompletedEvent;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.facade.WalletFacade;
import com.example.wallet.repository.InMemoryAccountRepository;
import com.example.wallet.service.InMemoryTransferExecutionService;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferHandlerTest {

    private InMemoryAccountRepository accounts;

    @Mock
    private EventPublisher eventPublisher;

    private TransferHandler handler;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        var transfer = new InMemoryTransferExecutionService(accounts, eventPublisher);
        var wallet = new WalletFacade(accounts, eventPublisher, transfer);
        handler = new TransferHandler(wallet);
    }

    @Test
    void transfersAndIsIdempotent() {
        var a = accounts.createWithInitialBalance(new BigDecimal("100.00"));
        var b = accounts.createWithInitialBalance(BigDecimal.ZERO);

        String tx = "tx-1";
        TransferResult first = handler.handle(tx, a.getId(), b.getId(), new BigDecimal("40.00"));
        assertThat(first.idempotentReplay()).isFalse();
        assertThat(first.fromBalanceAfter()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(first.toBalanceAfter()).isEqualByComparingTo(new BigDecimal("40.00"));

        TransferResult second = handler.handle(tx, a.getId(), b.getId(), new BigDecimal("40.00"));
        assertThat(second.idempotentReplay()).isTrue();
        assertThat(second.fromBalanceAfter()).isEqualByComparingTo(first.fromBalanceAfter());

        ArgumentCaptor<TransferCompletedEvent> captor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().transactionId()).isEqualTo(tx);
    }

    @Test
    void insufficientFundsDoesNotStoreIdempotency() {
        var a = accounts.createWithInitialBalance(new BigDecimal("10.00"));
        var b = accounts.createWithInitialBalance(BigDecimal.ZERO);

        assertThatThrownBy(() -> handler.handle("tx-fail", a.getId(), b.getId(), new BigDecimal("20.00")))
                .isInstanceOf(InsufficientFundsException.class);

        verifyNoInteractions(eventPublisher);

        TransferResult retry = handler.handle("tx-fail", a.getId(), b.getId(), new BigDecimal("5.00"));
        assertThat(retry.fromBalanceAfter()).isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
