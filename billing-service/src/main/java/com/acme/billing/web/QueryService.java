package com.acme.billing.web;

import com.acme.billing.account.*;
import com.acme.billing.core.AccountStatus;
import com.acme.billing.events.*;
import com.acme.billing.integrations.DemoDestination;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
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
    public QueryService(AccountRepository accounts, SyncRepository syncs, OutboxRepository events, ObjectProvider<DemoDestination> demo) {
        this.accounts = accounts; this.syncs = syncs; this.events = events; this.demo = demo;
    }
    public List<Views.Account> accounts() {
        var cursors = syncs.findAll().stream().collect(Collectors.toMap(s -> s.accountId, Function.identity()));
        return accounts.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(account -> {
            var sync = cursors.get(account.id);
            return Views.Account.of(account, sync, sync != null && sync.syncedVersion >= account.version ? "SYNCED" : "PENDING");
        }).toList();
    }
    public Views.Account account(String id) {
        return accounts().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Account not found"));
    }
    public List<Views.Event> events(String accountId) {
        return (accountId == null ? events.findTop100ByOrderByCreatedAtDesc()
                : events.findByAccountIdOrderByCreatedAtDesc(accountId)).stream().map(Views.Event::of).toList();
    }
    public Views.Overview overview() {
        var list = accounts();
        var revenue = list.stream().filter(a -> a.status() == AccountStatus.ACTIVE)
                .map(a -> new BigDecimal(a.monthlyRate())).reduce(new BigDecimal("0.00"), BigDecimal::add);
        var destination = demo.getIfAvailable();
        return new Views.Overview(list.size(), list.stream().filter(a -> a.status() == AccountStatus.ACTIVE).count(), revenue.toPlainString(),
                list.stream().filter(a -> a.syncStatus().equals("SYNCED")).count(),
                events.countByStatus(EventStatus.PENDING) + events.countByStatus(EventStatus.PUBLISHED) + events.countByStatus(EventStatus.RETRYING),
                events.countByStatus(EventStatus.DEAD_LETTER), events.countByStatus(EventStatus.DELIVERED),
                destination != null, destination == null || destination.isAvailable());
    }
}
