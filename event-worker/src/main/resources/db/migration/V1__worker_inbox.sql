CREATE TABLE worker_account_cursors (
    account_id VARCHAR(36) PRIMARY KEY,
    synced_version BIGINT NOT NULL,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP(6)
);
CREATE TABLE inbox_messages (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    account_version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    cohort VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    generation INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    revision BIGINT NOT NULL,
    published_revision BIGINT NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    claim_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    last_error VARCHAR(250),
    FOREIGN KEY (account_id) REFERENCES worker_account_cursors(account_id)
);
CREATE INDEX idx_worker_due ON inbox_messages(cohort, status, next_attempt_at);
CREATE INDEX idx_worker_reports ON inbox_messages(cohort, revision, published_revision);
