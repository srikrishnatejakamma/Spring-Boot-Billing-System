package com.acme.billing.web;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import com.acme.billing.events.*;
import java.time.Instant;

public final class Views {
    private Views() {}
    public record Account(String id, String company, String billingEmail, Plan plan, AccountStatus status,
                          String monthlyRate, String currency, long version, String syncStatus,
                          Long syncedVersion, Instant createdAt, Instant updatedAt, String outstandingBalance) {
        public static Account of(BillingAccount account, AccountSync sync, String syncStatus) {
            return new Account(account.id, account.company, account.billingEmail, account.plan, account.status,
                    account.monthlyRate.toPlainString(), account.currency, account.version, syncStatus,
                    sync == null || sync.syncedVersion < 0 ? null : sync.syncedVersion, account.createdAt, account.updatedAt, account.outstandingBalance.toPlainString());
        }
    }
    public record Event(String id, String accountId, long accountVersion, String type, EventStatus status,
                        int attempts, Instant createdAt, Instant nextAttemptAt, Instant deliveredAt, String lastError) {
        public static Event of(OutboxEvent event) {
            return new Event(event.id, event.accountId, event.accountVersion, event.type, event.status,
                    event.attempts, event.createdAt, event.nextAttemptAt, event.deliveredAt, event.lastError);
        }
    }
    public record Overview(long totalAccounts, long activeAccounts, String monthlyRevenue, long syncedAccounts,
                           long pendingEvents, long failedEvents, long deliveredEvents, boolean demo,
                           boolean platformAvailable) {}
}
