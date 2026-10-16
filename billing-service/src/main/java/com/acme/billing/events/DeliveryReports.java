package com.acme.billing.events;

import com.acme.billing.account.SyncRepository;
import com.acme.billing.dto.DeliveryReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class DeliveryReports {
    private final OutboxRepository events;
    private final SyncRepository syncs;
    public DeliveryReports(OutboxRepository events, SyncRepository syncs) { this.events = events; this.syncs = syncs; }
    @Transactional public void accept(DeliveryReport report) {
        var event = events.lock(report.eventId().toString()).orElseThrow(() -> new IllegalArgumentException("Unknown result event"));
        if (!event.accountId.equals(report.accountId().toString()) || event.accountVersion != report.accountVersion())
            throw new IllegalArgumentException("Result does not match the immutable event");
        if (report.generation() != event.generation || report.revision() <= event.resultRevision || event.status == EventStatus.DELIVERED) return;
        var status = EventStatus.valueOf(report.status());
        if (status != EventStatus.RETRYING && status != EventStatus.DEAD_LETTER && status != EventStatus.DELIVERED)
            throw new IllegalArgumentException("Invalid delivery result status");
        event.resultRevision = report.revision(); event.status = status; event.attempts = report.attempts();
        event.lastError = report.error(); event.nextAttemptAt = report.nextAttemptAt() == null ? Instant.now() : report.nextAttemptAt();
        if (status == EventStatus.DELIVERED) {
            var cursor = syncs.lock(event.accountId).orElseThrow();
            if (event.accountVersion > cursor.syncedVersion) {
                cursor.syncedVersion = event.accountVersion;
                cursor.syncedAt = report.completedAt() == null ? Instant.now() : report.completedAt();
            }
            event.deliveredAt = report.completedAt() == null ? Instant.now() : report.completedAt(); event.lastError = null;
        }
    }
}
