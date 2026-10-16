package com.acme.billing.dto;

import com.acme.billing.core.*;

import java.time.Instant;
import java.util.UUID;

public record AccountChanged(UUID eventId, String type, Instant occurredAt, AccountSnapshot account) {}
