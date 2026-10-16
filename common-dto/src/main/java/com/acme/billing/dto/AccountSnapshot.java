package com.acme.billing.dto;

import com.acme.billing.core.*;

import java.util.UUID;

/** Full-state, versioned message; money remains a decimal string at every API boundary. */
public record AccountSnapshot(UUID id, String company, String billingEmail, Plan plan,
                              AccountStatus status, String monthlyRate, String currency, long version) {}
