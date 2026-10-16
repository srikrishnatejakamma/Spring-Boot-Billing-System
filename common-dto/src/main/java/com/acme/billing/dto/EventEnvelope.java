package com.acme.billing.dto;

import java.util.Objects;

/** Additive v1 contract. Replays retain the original event ID and payload. */
public record EventEnvelope(int schemaVersion, AccountChanged change, int generation) {
    public EventEnvelope {
        Objects.requireNonNull(change, "change");
        if (schemaVersion != 1 || generation < 0) throw new IllegalArgumentException("Unsupported event envelope");
    }
}
