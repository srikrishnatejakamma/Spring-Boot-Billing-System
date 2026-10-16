ALTER TABLE billing_accounts ADD COLUMN outstanding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE outbox_events ADD COLUMN generation INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN result_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN routing_key VARCHAR(80) NOT NULL DEFAULT 'account.changed';
CREATE TABLE ledger_accounts (
    id VARCHAR(30) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    currency VARCHAR(3) NOT NULL
);
INSERT INTO ledger_accounts(id, name, currency) VALUES
    ('AR_USD', 'Accounts receivable', 'USD'),
    ('REVENUE_USD', 'Subscription revenue', 'USD'),
    ('CASH_USD', 'Cash received', 'USD');
CREATE TABLE ledger_transactions (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    description VARCHAR(200) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    account_version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES billing_accounts(id),
    CONSTRAINT positive_transaction_amount CHECK (amount > 0)
);
CREATE TABLE ledger_entries (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    ledger_account_id VARCHAR(30) NOT NULL,
    side VARCHAR(6) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id),
    FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts(id),
    CONSTRAINT positive_entry_amount CHECK (amount > 0),
    CONSTRAINT valid_entry_side CHECK (side IN ('DEBIT', 'CREDIT'))
);
CREATE INDEX idx_ledger_history ON ledger_transactions(account_id, created_at);
CREATE INDEX idx_entries_transaction ON ledger_entries(transaction_id);
