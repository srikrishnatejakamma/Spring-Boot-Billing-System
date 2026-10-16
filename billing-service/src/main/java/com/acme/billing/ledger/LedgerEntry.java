package com.acme.billing.ledger;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;

@Entity @Immutable @Table(name = "ledger_entries")
public class LedgerEntry {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 36) public String transactionId;
    @Column(nullable = false, length = 30) public String ledgerAccountId;
    @Column(nullable = false, length = 6) public String side;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal amount;
}
