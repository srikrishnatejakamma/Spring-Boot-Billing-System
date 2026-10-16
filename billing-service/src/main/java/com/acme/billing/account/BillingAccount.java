package com.acme.billing.account;

import com.acme.billing.dto.*;

import com.acme.billing.core.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_accounts")
public class BillingAccount {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 120) public String company;
    @Column(nullable = false, length = 254) public String billingEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public Plan plan;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public AccountStatus status;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal monthlyRate;
    @Column(nullable = false, length = 3) public String currency = "USD";
    @Version @Column(nullable = false) public Long version;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;

    public AccountSnapshot snapshot() {
        return new AccountSnapshot(UUID.fromString(id), company, billingEmail, plan, status,
                monthlyRate.toPlainString(), currency, version);
    }
}
