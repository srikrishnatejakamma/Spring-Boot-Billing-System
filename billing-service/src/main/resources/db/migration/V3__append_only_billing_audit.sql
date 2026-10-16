CREATE TABLE billing_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    justification VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_billing_audit_time ON billing_audit_log(occurred_at);
CREATE INDEX idx_billing_audit_target ON billing_audit_log(target_id, occurred_at);
