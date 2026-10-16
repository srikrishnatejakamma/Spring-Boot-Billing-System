package com.acme.billing.dto;

import com.acme.billing.core.*;

import java.util.UUID;

/** Full-state, versioned message; money remains a decimal string at every API boundary. */
public record AccountSnapshot(UUID id, String company, String billingEmail, Plan plan,
                              AccountStatus status, String monthlyRate, String currency, long version,
                              String outstandingBalance) {
    public AccountSnapshot(UUID id, String company, String billingEmail, Plan plan, AccountStatus status,
                           String monthlyRate, String currency, long version) {
        this(id, company, billingEmail, plan, status, monthlyRate, currency, version, "0.00");
    }
}
