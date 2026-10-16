package com.acme.billing.web;
import com.acme.billing.account.AccountRepository;
import com.netflix.graphql.dgs.DgsDataLoader;
import org.dataloader.MappedBatchLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@DgsDataLoader(name = "billingEmails")
public class EmailLoader implements MappedBatchLoader<String, String> {
    private final AccountRepository accounts;
    public EmailLoader(AccountRepository accounts) { this.accounts = accounts; }
    @Override public CompletionStage<Map<String, String>> load(Set<String> keys) {
        return CompletableFuture.completedFuture(accounts.findAllById(keys).stream().collect(Collectors.toMap(a -> a.id, a -> a.billingEmail)));
    }
}
