package com.acme.billing.web;
import com.acme.billing.ledger.*;
import com.netflix.graphql.dgs.DgsDataLoader;
import org.dataloader.MappedBatchLoader;
import java.util.*;
import java.util.concurrent.*;

@DgsDataLoader(name = "transactions")
public class TransactionLoader implements MappedBatchLoader<String, List<LedgerViews.Transaction>> {
    private final LedgerService ledger;
    public TransactionLoader(LedgerService ledger) { this.ledger = ledger; }
    @Override public CompletionStage<Map<String, List<LedgerViews.Transaction>>> load(Set<String> keys) {
        return CompletableFuture.completedFuture(ledger.batch(keys));
    }
}
