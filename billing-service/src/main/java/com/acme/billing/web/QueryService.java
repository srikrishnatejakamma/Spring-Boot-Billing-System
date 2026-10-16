package com.acme.billing.web;

import com.acme.billing.account.*;
import com.acme.billing.core.AccountStatus;
import com.acme.billing.events.*;
import com.acme.billing.integrations.DemoDestination;
import com.acme.platform.AccessPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @Transactional(readOnly = true)
public class QueryService {
    private final AccountRepository accounts;
    private final SyncRepository syncs;
    private final OutboxRepository events;
    private final ObjectProvider<DemoDestination> demo;
    private final AccessPolicy access;
    public QueryService(AccountRepository accounts, SyncRepository syncs, OutboxRepository events, ObjectProvider<DemoDestination> demo, AccessPolicy access) {
        this.accounts = accounts; this.syncs = syncs; this.events = events; this.demo = demo; this.access = access;
    }
    public List<Views.Account> accounts() {
        return accounts(100);
    }
    public List<Views.Account> accounts(int limit) {
        if (limit < 1 || limit > 100) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        var page = accounts.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        var cursors = syncs.findAllById(page.stream().map(a -> a.id).toList()).stream().collect(Collectors.toMap(s -> s.accountId, Function.identity()));
        return page.stream().map(account -> {
            var sync = cursors.get(account.id);
            return view(account, sync);
        }).toList();
    }
    public Views.Account account(String id) {
        var account = accounts.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Account not found"));
        return view(account, syncs.findById(id).orElse(null));
    }
    private Views.Account view(BillingAccount account, AccountSync sync) {
        var view = Views.Account.of(account, sync, sync != null && sync.syncedVersion >= account.version ? "SYNCED" : "PENDING");
        if (access.allows("billing.pii")) return view;
        return new Views.Account(view.id(), view.company(), null, view.plan(), view.status(), view.monthlyRate(), view.currency(),
                view.version(), view.syncStatus(), view.syncedVersion(), view.createdAt(), view.updatedAt(), view.outstandingBalance());
    }
    public List<Views.Event> events(String accountId) {
        return (accountId == null ? events.findTop100ByOrderByCreatedAtDesc()
                : events.findByAccountIdOrderByCreatedAtDesc(accountId)).stream().map(Views.Event::of).toList();
    }
    public Views.Overview overview() {
        var revenue = accounts.recurringRevenue().setScale(2);
        var destination = demo.getIfAvailable();
        return new Views.Overview(accounts.count(), accounts.countByStatus(AccountStatus.ACTIVE), revenue.toPlainString(),
                accounts.countSynchronized(),
                events.countByStatus(EventStatus.PENDING) + events.countByStatus(EventStatus.PUBLISHED) + events.countByStatus(EventStatus.RETRYING),
                events.countByStatus(EventStatus.DEAD_LETTER), events.countByStatus(EventStatus.DELIVERED),
                destination != null, destination == null || destination.isAvailable());
    }
}
