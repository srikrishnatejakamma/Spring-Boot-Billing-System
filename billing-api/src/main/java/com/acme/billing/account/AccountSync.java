package com.acme.billing.account;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "account_sync")
public class AccountSync {
    @Id @Column(length = 36) public String accountId;
    @Column(nullable = false) public long syncedVersion = -1;
    public Instant syncedAt;
    public AccountSync() {}
    public AccountSync(String accountId) { this.accountId = accountId; }
}
