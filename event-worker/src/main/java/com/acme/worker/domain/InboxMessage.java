package com.acme.worker.domain;

import com.acme.billing.dto.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "inbox_messages")
public class InboxMessage {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 36) public String accountId;
    @Column(nullable = false) public long accountVersion;
    @Column(nullable = false, columnDefinition = "TEXT") public String payload;
    @Column(nullable = false, length = 40) public String cohort;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public InboxStatus status = InboxStatus.PENDING;
    @Column(nullable = false) public int generation;
    @Column(nullable = false) public int attempts;
    @Column(nullable = false) public long revision;
    @Column(nullable = false) public long publishedRevision;
    @Column(nullable = false) public Instant receivedAt;
    @Column(nullable = false) public Instant nextAttemptAt;
    @Column(length = 36) public String claimToken;
    public Instant leaseUntil;
    public Instant completedAt;
    @Column(length = 250) public String lastError;

    public InboxMessage() {}
    public InboxMessage(EventEnvelope envelope, String payload, String cohort) {
        id = envelope.change().eventId().toString(); accountId = envelope.change().account().id().toString();
        accountVersion = envelope.change().account().version(); generation = envelope.generation();
        this.payload = payload; this.cohort = cohort; receivedAt = nextAttemptAt = Instant.now();
    }
    public void complete() {
        status = InboxStatus.DELIVERED; completedAt = Instant.now(); lastError = null;
        claimToken = null; leaseUntil = null; revision++;
    }
    public void fail(String reason, boolean permanent) {
        attempts++; status = permanent || attempts >= 6 ? InboxStatus.DEAD_LETTER : InboxStatus.RETRYING;
        deferFields(reason, Math.min(60, 1L << Math.min(attempts, 6))); revision++;
    }
    public void defer(String reason) {
        status = InboxStatus.RETRYING; deferFields(reason, 5); revision++;
    }
    private void deferFields(String reason, long seconds) {
        lastError = reason; nextAttemptAt = Instant.now().plusSeconds(seconds); claimToken = null; leaseUntil = null;
    }
    public void replay(int newGeneration) {
        generation = newGeneration;
        if (status == InboxStatus.DELIVERED) { revision++; return; }
        status = InboxStatus.PENDING; attempts = 0; nextAttemptAt = Instant.now(); lastError = null;
        claimToken = null; leaseUntil = null;
    }
    public DeliveryReport report() {
        return new DeliveryReport(java.util.UUID.fromString(id), java.util.UUID.fromString(accountId), accountVersion,
                generation, revision, status.name(), attempts, nextAttemptAt, completedAt, lastError);
    }
}
