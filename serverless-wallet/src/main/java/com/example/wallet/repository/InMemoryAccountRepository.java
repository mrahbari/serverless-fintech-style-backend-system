package com.example.wallet.repository;

import com.example.wallet.domain.Account;
import com.example.wallet.exception.AccountNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository when {@code wallet.aws.enabled=false} (tests / local without AWS).
 */
@Repository
@ConditionalOnProperty(name = "wallet.aws.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Account save(Account account) {
        accounts.put(account.getId(), account);
        return account;
    }

    @Override
    public Account createWithInitialBalance(BigDecimal initialBalance) {
        Account a = Account.newAccount(initialBalance);
        accounts.put(a.getId(), a);
        return a;
    }

    @Override
    public synchronized BigDecimal depositAdd(String accountId, BigDecimal amount) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        var newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        return newBalance;
    }

    @Override
    public AccountPage findAccountsAfterCursor(String cursorExclusive, int limit) {
        if (limit <= 0) {
            return new AccountPage(List.of(), null);
        }
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparing(Account::getId));
        int start = 0;
        if (cursorExclusive != null && !cursorExclusive.isBlank()) {
            while (start < sorted.size() && sorted.get(start).getId().compareTo(cursorExclusive) <= 0) {
                start++;
            }
        }
        if (start >= sorted.size()) {
            return new AccountPage(List.of(), null);
        }
        int end = Math.min(start + limit, sorted.size());
        List<Account> slice = List.copyOf(sorted.subList(start, end));
        String nextCursor = end < sorted.size() ? slice.get(slice.size() - 1).getId() : null;
        return new AccountPage(slice, nextCursor);
    }

    @Override
    public List<Account> findByIdsIn(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Account> out = new ArrayList<>();
        for (String id : ids) {
            Account a = accounts.get(id);
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }
}
