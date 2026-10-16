package com.acme.billing.account;

import jakarta.persistence.*;

@Entity @Table(name = "idempotency_keys")
public class IdempotencyRecord {
    @Id @Column(name = "request_key", length = 100) public String key;
    @Column(nullable = false, length = 64) public String fingerprint;
    @Column(nullable = false, length = 36) public String accountId;
    protected IdempotencyRecord() {}
    public IdempotencyRecord(String key, String fingerprint, String accountId) {
        this.key = key; this.fingerprint = fingerprint; this.accountId = accountId;
    }
}
