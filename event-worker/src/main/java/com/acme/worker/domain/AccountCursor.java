package com.acme.worker.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "worker_account_cursors")
public class AccountCursor {
    @Id @Column(length = 36) public String accountId;
    @Column(nullable = false) public long syncedVersion = -1;
    @Column(length = 36) public String leaseToken;
    public Instant leaseUntil;
    public AccountCursor() {}
    public AccountCursor(String id) { accountId = id; }
    public boolean available(Instant now) { return leaseToken == null || leaseUntil == null || !leaseUntil.isAfter(now); }
    public void release() { leaseToken = null; leaseUntil = null; }
}
