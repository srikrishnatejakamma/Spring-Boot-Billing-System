package com.acme.billing.events;

import com.acme.billing.dto.*;

import com.acme.billing.account.SyncRepository;
import com.acme.billing.integrations.AccountDestination;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class DeliveryProcessor {
    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    private final OutboxRepository events;
    private final SyncRepository syncs;
    private final AccountDestination destination;
    private final ObjectMapper json;
    private final MeterRegistry metrics;

    public DeliveryProcessor(OutboxRepository events, SyncRepository syncs, AccountDestination destination,
                             ObjectMapper json, MeterRegistry metrics) {
        this.events = events; this.syncs = syncs; this.destination = destination; this.json = json; this.metrics = metrics;
    }

    @Transactional
    public void receive(String eventId) {
        // The durable outbox row is also the inbox receipt. A duplicate Rabbit delivery is a no-op.
        var event = events.lock(eventId).orElseThrow(() -> new IllegalArgumentException("Unknown event ID"));
        if (event.status == EventStatus.DELIVERED || event.status == EventStatus.DEAD_LETTER) {
            metrics.counter("billing.delivery.duplicates").increment(); return;
        }
        if (event.status == EventStatus.RETRYING && event.nextAttemptAt.isAfter(Instant.now())) return;
        deliverLocked(event);
    }

    public void deliverLocked(OutboxEvent event) {
        var cursor = syncs.lock(event.accountId).orElseThrow();
        if (event.accountVersion <= cursor.syncedVersion) {
            event.status = EventStatus.DELIVERED;
            event.deliveredAt = Instant.now();
            event.lastError = null;
            metrics.counter("billing.delivery.duplicates").increment();
            return;
        }
        try {
            AccountChanged message = json.readValue(event.payload, AccountChanged.class);
            destination.synchronize(message);
            cursor.syncedVersion = event.accountVersion;
            cursor.syncedAt = Instant.now();
            event.status = EventStatus.DELIVERED;
            event.deliveredAt = cursor.syncedAt;
            event.lastError = null;
            metrics.counter("billing.delivery.success").increment();
            log.info("account_synced eventId={} accountId={} version={}", event.id, event.accountId, event.accountVersion);
        } catch (Exception e) {
            event.fail(destination instanceof com.acme.billing.integrations.DemoDestination
                    ? "Demo billing platform is unavailable" : "Destination synchronization failed; investigate using the event ID");
            metrics.counter("billing.delivery.failures").increment();
            log.warn("account_sync_failed eventId={} accountId={} attempt={} status={} cause={}", event.id, event.accountId, event.attempts, event.status, e.getClass().getSimpleName());
        }
    }
}
