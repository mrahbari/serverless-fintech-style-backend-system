package com.example.wallet.repository;

import com.example.wallet.domain.Account;
import com.example.wallet.exception.AccountNotFoundException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB-backed accounts table (PK {@code accountId}). Listing uses a full table scan then in-memory sort
 * and cursor slicing (acceptable for demos; production would use a GSI or query pattern).
 */
public class DynamoDbAccountRepository implements AccountRepository {

    public static final String ATTR_ACCOUNT_ID = "accountId";
    public static final String ATTR_BALANCE = "balance";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbAccountRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public Optional<Account> findById(String id) {
        var resp = dynamoDb.getItem(r -> r.tableName(tableName).key(Map.of(ATTR_ACCOUNT_ID, AttributeValue.fromS(id))));
        if (!resp.hasItem() || resp.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toAccount(resp.item()));
    }

    @Override
    public Account save(Account account) {
        dynamoDb.putItem(r -> r.tableName(tableName).item(toItem(account)));
        return account;
    }

    @Override
    public Account createWithInitialBalance(BigDecimal initialBalance) {
        Account a = Account.newAccount(initialBalance);
        dynamoDb.putItem(r -> r.tableName(tableName).item(toItem(a)));
        return a;
    }

    @Override
    public BigDecimal depositAdd(String accountId, BigDecimal amount) {
        try {
            var resp = dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(ATTR_ACCOUNT_ID, AttributeValue.fromS(accountId)))
                    .updateExpression("ADD #b :amt")
                    .expressionAttributeNames(Map.of("#b", ATTR_BALANCE))
                    .expressionAttributeValues(Map.of(":amt", AttributeValue.fromN(amount.toPlainString())))
                    .conditionExpression("attribute_exists(#b)")
                    .returnValues(ReturnValue.ALL_NEW)
                    .build());
            return new BigDecimal(resp.attributes().get(ATTR_BALANCE).n());
        } catch (ConditionalCheckFailedException e) {
            throw new AccountNotFoundException(accountId);
        }
    }

    @Override
    public AccountPage findAccountsAfterCursor(String cursorExclusive, int limit) {
        if (limit <= 0) {
            return new AccountPage(List.of(), null);
        }
        List<Account> all = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            var scanBuilder = ScanRequest.builder().tableName(tableName).limit(100);
            if (startKey != null && !startKey.isEmpty()) {
                scanBuilder.exclusiveStartKey(startKey);
            }
            var scan = dynamoDb.scan(scanBuilder.build());
            for (var item : scan.items()) {
                all.add(toAccount(item));
            }
            startKey = scan.lastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());

        all.sort(Comparator.comparing(Account::getId));
        int start = 0;
        if (cursorExclusive != null && !cursorExclusive.isBlank()) {
            while (start < all.size() && all.get(start).getId().compareTo(cursorExclusive) <= 0) {
                start++;
            }
        }
        if (start >= all.size()) {
            return new AccountPage(List.of(), null);
        }
        int end = Math.min(start + limit, all.size());
        List<Account> slice = List.copyOf(all.subList(start, end));
        String nextCursor = end < all.size() ? slice.get(slice.size() - 1).getId() : null;
        return new AccountPage(slice, nextCursor);
    }

    @Override
    public List<Account> findByIdsIn(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Account> out = new ArrayList<>();
        var distinct = ids.stream().distinct().collect(Collectors.toList());
        int i = 0;
        while (i < distinct.size()) {
            int end = Math.min(i + 100, distinct.size());
            var chunk = distinct.subList(i, end);
            var keys = chunk.stream()
                    .map(id -> Map.of(ATTR_ACCOUNT_ID, AttributeValue.fromS(id)))
                    .collect(Collectors.toList());
            var resp = dynamoDb.batchGetItem(BatchGetItemRequest.builder()
                    .requestItems(Map.of(
                            tableName,
                            KeysAndAttributes.builder().keys(keys).build()))
                    .build());
            var items = resp.responses().get(tableName);
            if (items != null) {
                for (var item : items) {
                    out.add(toAccount(item));
                }
            }
            i = end;
        }
        return out;
    }

    public static Map<String, AttributeValue> toItem(Account account) {
        Map<String, AttributeValue> m = new HashMap<>();
        m.put(ATTR_ACCOUNT_ID, AttributeValue.fromS(account.getId()));
        m.put(ATTR_BALANCE, AttributeValue.fromN(account.getBalance().toPlainString()));
        return m;
    }

    public static Account toAccount(Map<String, AttributeValue> item) {
        return new Account(item.get(ATTR_ACCOUNT_ID).s(), new BigDecimal(item.get(ATTR_BALANCE).n()));
    }
}
