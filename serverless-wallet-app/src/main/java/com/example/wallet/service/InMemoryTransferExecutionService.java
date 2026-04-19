package com.example.wallet.service;

import com.example.wallet.domain.TransferResult;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.event.TransferCompletedEvent;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process transfer + idempotency when {@code wallet.aws.enabled=false}. Single lock serializes transfers
 * in this JVM (not distributed); DynamoDB path uses {@link DynamoDbTransferExecutionService}.
 */
@Component
@ConditionalOnProperty(name = "wallet.aws.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTransferExecutionService implements TransferExecutionService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransferExecutionService.class);

    private final AccountRepository accounts;
    private final EventPublisher eventPublisher;
    private final ConcurrentHashMap<String, TransferResult> completed = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public InMemoryTransferExecutionService(AccountRepository accounts, EventPublisher eventPublisher) {
        this.accounts = accounts;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TransferResult executeTransfer(
            String transactionId, String fromAccountId, String toAccountId, BigDecimal amount) {
        synchronized (lock) {
            var cached = completed.get(transactionId);
            if (cached != null) {
                log.info("handler_transfer idempotent_replay transactionId={}", transactionId);
                return new TransferResult(
                        cached.transactionId(),
                        cached.fromAccountId(),
                        cached.toAccountId(),
                        cached.amount(),
                        cached.fromBalanceAfter(),
                        cached.toBalanceAfter(),
                        true);
            }
            TransferResult executed = executeOnce(transactionId, fromAccountId, toAccountId, amount);
            completed.put(transactionId, executed);
            return new TransferResult(
                    executed.transactionId(),
                    executed.fromAccountId(),
                    executed.toAccountId(),
                    executed.amount(),
                    executed.fromBalanceAfter(),
                    executed.toBalanceAfter(),
                    false);
        }
    }

    private TransferResult executeOnce(
            String transactionId, String fromAccountId, String toAccountId, BigDecimal amount) {
        log.info("handler_transfer start transactionId={} from={} to={} amount={}", transactionId, fromAccountId, toAccountId, amount);
        var from = accounts.findById(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        var to = accounts.findById(toAccountId).orElseThrow(() -> new AccountNotFoundException(toAccountId));
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccountId, amount, from.getBalance());
        }
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accounts.save(from);
        accounts.save(to);
        var event = new TransferCompletedEvent(
                transactionId,
                fromAccountId,
                toAccountId,
                amount,
                from.getBalance(),
                to.getBalance(),
                Instant.now());
        eventPublisher.publish(event);
        log.info(
                "handler_transfer completed transactionId={} fromBalanceAfter={} toBalanceAfter={}",
                transactionId,
                from.getBalance(),
                to.getBalance());
        return new TransferResult(transactionId, fromAccountId, toAccountId, amount, from.getBalance(), to.getBalance(), false);
    }
}
