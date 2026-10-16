package com.acme.billing.web;
import com.acme.billing.ledger.*;
import com.netflix.graphql.dgs.DgsDataLoader;
import org.dataloader.MappedBatchLoader;
import java.util.*;
import java.util.concurrent.*;

@DgsDataLoader(name = "entries")
public class EntryLoader implements MappedBatchLoader<String, List<LedgerViews.Entry>> {
    private final LedgerService ledger;
    public EntryLoader(LedgerService ledger) { this.ledger = ledger; }
    @Override public CompletionStage<Map<String, List<LedgerViews.Entry>>> load(Set<String> keys) {
        return CompletableFuture.completedFuture(ledger.batchEntries(keys));
    }
}
