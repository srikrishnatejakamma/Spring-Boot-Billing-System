package com.acme.billing.events;

import com.acme.billing.dto.*;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "outbox_events")
public class OutboxEvent {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 36) public String accountId;
    @Column(nullable = false) public long accountVersion;
    @Column(nullable = false, length = 40) public String type;
    @Column(nullable = false, columnDefinition = "TEXT") public String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public EventStatus status;
    @Column(nullable = false) public int attempts;
    @Column(nullable = false) public int generation;
    @Column(nullable = false) public long resultRevision;
    @Column(nullable = false, length = 80) public String routingKey = "account.changed";
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant nextAttemptAt;
    public Instant deliveredAt;
    @Column(length = 500) public String lastError;

    public OutboxEvent() {}
    public OutboxEvent(AccountChanged event, String payload) {
        this.id = event.eventId().toString(); this.accountId = event.account().id().toString();
        this.accountVersion = event.account().version(); this.type = event.type(); this.payload = payload;
        this.createdAt = event.occurredAt(); this.nextAttemptAt = createdAt; this.status = EventStatus.PENDING;
    }
    public void fail(String error) {
        attempts++;
        lastError = error == null ? "Delivery failed" : error.substring(0, Math.min(error.length(), 500));
        status = attempts >= 6 ? EventStatus.DEAD_LETTER : EventStatus.RETRYING;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(60, 1L << Math.min(attempts, 6)));
    }
}
