package com.example.wallet.repository;

import com.example.wallet.domain.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findById(String id);

    Account save(Account account);

    Account createWithInitialBalance(BigDecimal initialBalance);

    /**
     * Atomic add to balance (DynamoDB ADD; in-memory under lock).
     */
    BigDecimal depositAdd(String accountId, BigDecimal amount);

    AccountPage findAccountsAfterCursor(String cursorExclusive, int limit);

    List<Account> findByIdsIn(List<String> ids);
}
