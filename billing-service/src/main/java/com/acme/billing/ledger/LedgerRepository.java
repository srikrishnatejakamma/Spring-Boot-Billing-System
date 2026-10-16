package com.acme.billing.ledger;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LedgerRepository extends JpaRepository<LedgerTransaction, String> {
    Optional<LedgerTransaction> findByRequestKey(String key);
    List<LedgerTransaction> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable page);
    @Query(value = "SELECT ranked.* FROM (SELECT t.*, ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY created_at DESC, id) AS row_rank FROM ledger_transactions t WHERE account_id IN (:ids)) ranked WHERE ranked.row_rank <= 100", nativeQuery = true)
    List<LedgerTransaction> batchHistory(@Param("ids") Collection<String> accountIds);
}
