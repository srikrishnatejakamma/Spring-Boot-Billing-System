package com.acme.billing.ledger;

import com.acme.billing.core.TransactionKind;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Immutable @Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 36) public String accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public TransactionKind kind;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal amount;
    @Column(nullable = false, length = 200) public String description;
    @Column(nullable = false, length = 150) public String actor;
    @Column(nullable = false, length = 100, unique = true) public String requestKey;
    @Column(nullable = false, length = 64) public String fingerprint;
    @Column(nullable = false) public long accountVersion;
    @Column(nullable = false) public Instant createdAt;
}
