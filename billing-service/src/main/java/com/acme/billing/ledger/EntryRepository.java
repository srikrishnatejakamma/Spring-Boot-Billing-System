package com.acme.billing.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface EntryRepository extends JpaRepository<LedgerEntry, String> {
    List<LedgerEntry> findByTransactionIdIn(Collection<String> ids);
}
