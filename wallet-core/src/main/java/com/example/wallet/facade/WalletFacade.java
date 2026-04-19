package com.example.wallet.facade;

import com.example.wallet.domain.TransferResult;
import com.example.wallet.event.AccountCreatedEvent;
import com.example.wallet.event.DepositCompletedEvent;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.service.TransferExecutionService;
import com.example.wallet.view.AccountListResponse;
import com.example.wallet.view.AccountSummary;
import com.example.wallet.view.CreateAccountResult;
import com.example.wallet.view.DepositResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Shared use cases for Spring Boot and Lambda (no framework dependencies).
 */
public class WalletFacade {

    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AccountRepository accounts;
    private final EventPublisher eventPublisher;
    private final TransferExecutionService transferExecution;

    public WalletFacade(
            AccountRepository accounts,
            EventPublisher eventPublisher,
            TransferExecutionService transferExecution) {
        this.accounts = accounts;
        this.eventPublisher = eventPublisher;
        this.transferExecution = transferExecution;
    }

    public CreateAccountResult createAccount(BigDecimal initialBalance) {
        BigDecimal balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("initialBalance must be non-negative");
        }
        log.info("handler_create_account initialBalance={}", balance);
        var account = accounts.createWithInitialBalance(balance);
        var event = new AccountCreatedEvent(account.getId(), balance, Instant.now());
        eventPublisher.publish(event);
        log.info("handler_create_account completed accountId={}", account.getId());
        return new CreateAccountResult(account.getId(), account.getBalance());
    }

    public DepositResult deposit(String accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        log.info("handler_deposit accountId={} amount={}", accountId, amount);
        BigDecimal newBalance = accounts.depositAdd(accountId, amount);
        eventPublisher.publish(new DepositCompletedEvent(accountId, amount, newBalance, Instant.now()));
        log.info("handler_deposit completed accountId={} newBalance={}", accountId, newBalance);
        return new DepositResult(accountId, newBalance);
    }

    public TransferResult transfer(String transactionId, String fromAccountId, String toAccountId, BigDecimal amount) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required for idempotent transfers");
        }
        if (fromAccountId == null || toAccountId == null || fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("from and to must be distinct account ids");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return transferExecution.executeTransfer(transactionId, fromAccountId, toAccountId, amount);
    }

    public AccountListResponse listAccounts(Integer limit, String cursor) {
        int lim = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(1, limit), MAX_LIMIT);
        log.info("handler_list_accounts limit={} cursor={}", lim, cursor);
        var page = accounts.findAccountsAfterCursor(cursor, lim);
        List<AccountSummary> summaries =
                page.items().stream().map(a -> new AccountSummary(a.getId(), a.getBalance())).toList();
        return new AccountListResponse(summaries, page.nextCursor());
    }

    public AccountSummary getAccount(String accountId) {
        log.info("handler_get_account accountId={}", accountId);
        return accounts
                .findById(accountId)
                .map(a -> new AccountSummary(a.getId(), a.getBalance()))
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
