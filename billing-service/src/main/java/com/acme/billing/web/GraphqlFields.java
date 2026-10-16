package com.acme.billing.web;

import com.acme.billing.ledger.LedgerViews;
import com.acme.platform.AccessPolicy;
import com.netflix.graphql.dgs.*;
import org.dataloader.DataLoader;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class GraphqlFields {
    private final AccessPolicy access;
    public GraphqlFields(AccessPolicy access) { this.access = access; }
    @DgsData(parentType = "Account", field = "transactions")
    public CompletableFuture<List<LedgerViews.Transaction>> transactions(DgsDataFetchingEnvironment environment) {
        Views.Account account = environment.getSource();
        DataLoader<String, List<LedgerViews.Transaction>> loader = environment.getDataLoader("transactions");
        return loader.load(account.id());
    }
    @DgsData(parentType = "LedgerTransaction", field = "entries")
    public CompletableFuture<List<LedgerViews.Entry>> entries(DgsDataFetchingEnvironment environment) {
        LedgerViews.Transaction transaction = environment.getSource();
        DataLoader<String, List<LedgerViews.Entry>> loader = environment.getDataLoader("entries");
        return loader.load(transaction.id());
    }
    @DgsData(parentType = "Account", field = "billingEmail")
    public CompletableFuture<String> email(DgsDataFetchingEnvironment environment) {
        Authentication authentication = environment.getGraphQlContext().get("authentication");
        if (!access.allows(authentication, "billing.pii")) throw new AccessDeniedException("Restricted billing field");
        Views.Account account = environment.getSource();
        DataLoader<String, String> loader = environment.getDataLoader("billingEmails");
        return loader.load(account.id());
    }
}
