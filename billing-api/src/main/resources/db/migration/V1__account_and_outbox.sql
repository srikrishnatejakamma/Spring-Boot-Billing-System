CREATE TABLE billing_accounts (
    id VARCHAR(36) PRIMARY KEY,
    company VARCHAR(120) NOT NULL,
    billing_email VARCHAR(254) NOT NULL,
    plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    monthly_rate DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT positive_rate CHECK (monthly_rate >= 0)
);
CREATE TABLE account_sync (
    account_id VARCHAR(36) PRIMARY KEY,
    synced_version BIGINT NOT NULL,
    synced_at TIMESTAMP(6),
    FOREIGN KEY (account_id) REFERENCES billing_accounts(id)
);
CREATE TABLE idempotency_keys (
    request_key VARCHAR(100) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (account_id) REFERENCES billing_accounts(id)
);
CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    account_version BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    delivered_at TIMESTAMP(6),
    last_error VARCHAR(500),
    FOREIGN KEY (account_id) REFERENCES billing_accounts(id),
    CONSTRAINT unique_account_version UNIQUE(account_id, account_version)
);
CREATE INDEX idx_outbox_due ON outbox_events(status, next_attempt_at);
CREATE INDEX idx_outbox_account ON outbox_events(account_id, created_at);
