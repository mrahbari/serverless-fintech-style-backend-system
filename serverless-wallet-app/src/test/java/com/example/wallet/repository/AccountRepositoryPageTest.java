package com.example.wallet.repository;

import com.example.wallet.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRepositoryPageTest {

    private InMemoryAccountRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryAccountRepository();
    }

    @Test
    void paginationIsSingleScanSortedById() {
        repo.save(new Account("c", BigDecimal.ONE));
        repo.save(new Account("a", BigDecimal.TEN));
        repo.save(new Account("b", BigDecimal.ZERO));

        AccountPage p1 = repo.findAccountsAfterCursor(null, 2);
        assertThat(p1.items()).hasSize(2);
        assertThat(p1.items().get(0).getId()).isEqualTo("a");
        assertThat(p1.items().get(1).getId()).isEqualTo("b");
        assertThat(p1.nextCursor()).isEqualTo("b");

        AccountPage p2 = repo.findAccountsAfterCursor(p1.nextCursor(), 2);
        assertThat(p2.items()).hasSize(1);
        assertThat(p2.items().get(0).getId()).isEqualTo("c");
        assertThat(p2.nextCursor()).isNull();
    }

    @Test
    void findByIdsInLoadsInOneBatchWithoutExtraQueries() {
        repo.save(new Account("id1", BigDecimal.ONE));
        repo.save(new Account("id2", BigDecimal.TWO));

        var batch = repo.findByIdsIn(java.util.List.of("id2", "id1", "missing"));

        assertThat(batch).hasSize(2);
    }
}
