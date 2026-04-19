package com.example.wallet.service;

import com.example.wallet.domain.TransferResult;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.event.TransferCompletedEvent;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.repository.DynamoDbAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Atomic transfer using DynamoDB {@code TransactWriteItems} (balance check, debit/credit, idempotency put).
 * Duplicate {@code transactionId} is detected via conditional put; callers then read the stored result.
 */
public class DynamoDbTransferExecutionService implements TransferExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTransferExecutionService.class);

    private static final String ATTR_TX = "transactionId";
    private static final String ATTR_FROM = "fromAccountId";
    private static final String ATTR_TO = "toAccountId";
    private static final String ATTR_AMOUNT = "amount";
    private static final String ATTR_FROM_AFTER = "fromBalanceAfter";
    private static final String ATTR_TO_AFTER = "toBalanceAfter";

    private final DynamoDbClient dynamoDb;
    private final String accountsTable;
    private final String idempotencyTable;
    private final AccountRepository accounts;
    private final EventPublisher eventPublisher;

    public DynamoDbTransferExecutionService(
            DynamoDbClient dynamoDb,
            String accountsTable,
            String idempotencyTable,
            AccountRepository accounts,
            EventPublisher eventPublisher) {
        this.dynamoDb = dynamoDb;
        this.accountsTable = accountsTable;
        this.idempotencyTable = idempotencyTable;
        this.accounts = accounts;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TransferResult executeTransfer(
            String transactionId, String fromAccountId, String toAccountId, BigDecimal amount) {
        Optional<TransferResult> existing = findIdempotency(transactionId);
        if (existing.isPresent()) {
            log.info("handler_transfer idempotent_replay transactionId={}", transactionId);
            return existing.get();
        }

        var from = accounts.findById(fromAccountId).orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        var to = accounts.findById(toAccountId).orElseThrow(() -> new AccountNotFoundException(toAccountId));
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccountId, amount, from.getBalance());
        }
        BigDecimal afterFrom = from.getBalance().subtract(amount);
        BigDecimal afterTo = to.getBalance().add(amount);

        var debit = TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(accountsTable)
                        .key(Map.of(DynamoDbAccountRepository.ATTR_ACCOUNT_ID, AttributeValue.fromS(fromAccountId)))
                        .updateExpression("SET #b = #b - :amt")
                        .conditionExpression("#b >= :amt")
                        .expressionAttributeNames(Map.of("#b", DynamoDbAccountRepository.ATTR_BALANCE))
                        .expressionAttributeValues(Map.of(
                                ":amt", AttributeValue.fromN(amount.toPlainString())))
                        .build())
                .build();

        var credit = TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(accountsTable)
                        .key(Map.of(DynamoDbAccountRepository.ATTR_ACCOUNT_ID, AttributeValue.fromS(toAccountId)))
                        .updateExpression("SET #b = #b + :amt")
                        .conditionExpression("attribute_exists(#pk)")
                        .expressionAttributeNames(
                                Map.of("#b", DynamoDbAccountRepository.ATTR_BALANCE, "#pk", DynamoDbAccountRepository.ATTR_ACCOUNT_ID))
                        .expressionAttributeValues(Map.of(":amt", AttributeValue.fromN(amount.toPlainString())))
                        .build())
                .build();

        Map<String, AttributeValue> idemItem = Map.of(
                ATTR_TX,
                AttributeValue.fromS(transactionId),
                ATTR_FROM,
                AttributeValue.fromS(fromAccountId),
                ATTR_TO,
                AttributeValue.fromS(toAccountId),
                ATTR_AMOUNT,
                AttributeValue.fromN(amount.toPlainString()),
                ATTR_FROM_AFTER,
                AttributeValue.fromN(afterFrom.toPlainString()),
                ATTR_TO_AFTER,
                AttributeValue.fromN(afterTo.toPlainString()));

        var idemPut = TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(idempotencyTable)
                        .item(idemItem)
                        .conditionExpression("attribute_not_exists(#tx)")
                        .expressionAttributeNames(Map.of("#tx", ATTR_TX))
                        .build())
                .build();

        try {
            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(debit, credit, idemPut)
                    .build());
        } catch (TransactionCanceledException e) {
            log.warn("dynamodb_transfer_tx_cancelled transactionId={} reasons={}", transactionId, e.cancellationReasons());
            Optional<TransferResult> raced = findIdempotency(transactionId);
            if (raced.isPresent()) {
                return raced.get();
            }
            throw new InsufficientFundsException(fromAccountId, amount, from.getBalance());
        }

        var event = new TransferCompletedEvent(
                transactionId,
                fromAccountId,
                toAccountId,
                amount,
                afterFrom,
                afterTo,
                Instant.now());
        eventPublisher.publish(event);
        log.info(
                "handler_transfer completed transactionId={} fromBalanceAfter={} toBalanceAfter={}",
                transactionId,
                afterFrom,
                afterTo);
        return new TransferResult(
                transactionId, fromAccountId, toAccountId, amount, afterFrom, afterTo, false);
    }

    private Optional<TransferResult> findIdempotency(String transactionId) {
        var resp = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(idempotencyTable)
                .key(Map.of(ATTR_TX, AttributeValue.fromS(transactionId)))
                .consistentRead(true)
                .build());
        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toTransferResult(resp.item()));
    }

    private static TransferResult toTransferResult(Map<String, AttributeValue> item) {
        return new TransferResult(
                item.get(ATTR_TX).s(),
                item.get(ATTR_FROM).s(),
                item.get(ATTR_TO).s(),
                new BigDecimal(item.get(ATTR_AMOUNT).n()),
                new BigDecimal(item.get(ATTR_FROM_AFTER).n()),
                new BigDecimal(item.get(ATTR_TO_AFTER).n()),
                true);
    }
}
