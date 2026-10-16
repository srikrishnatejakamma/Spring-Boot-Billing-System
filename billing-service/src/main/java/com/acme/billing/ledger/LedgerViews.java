package com.acme.billing.ledger;

import java.time.Instant;

public final class LedgerViews {
    private LedgerViews() {}
    public record Transaction(String id, String accountId, String kind, String amount, String description,
                              String actor, long accountVersion, Instant createdAt) {
        public static Transaction of(LedgerTransaction t) {
            return new Transaction(t.id, t.accountId, t.kind.name(), t.amount.toPlainString(), t.description, t.actor, t.accountVersion, t.createdAt);
        }
    }
    public record Entry(String id, String transactionId, String ledgerAccount, String side, String amount) {
        public static Entry of(LedgerEntry entry) {
            return new Entry(entry.id, entry.transactionId, entry.ledgerAccountId, entry.side, entry.amount.toPlainString());
        }
    }
}
