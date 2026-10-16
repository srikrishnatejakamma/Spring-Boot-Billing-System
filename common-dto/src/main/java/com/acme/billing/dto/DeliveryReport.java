package com.acme.billing.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryReport(UUID eventId, UUID accountId, long accountVersion, int generation,
                             long revision, String status, int attempts, Instant nextAttemptAt,
                             Instant completedAt, String error) {}
