package com.acme.billing.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class OutboxDispatcher {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxRepository events;
    private final DeliveryProcessor processor;
    private final EventTransport transport;
    private final boolean distributed;
    private final MeterRegistry metrics;

    public OutboxDispatcher(OutboxRepository events, DeliveryProcessor processor, EventTransport transport,
                            @Value("${billing.distributed:false}") boolean distributed, MeterRegistry metrics) {
        this.events = events; this.processor = processor; this.transport = transport;
        this.distributed = distributed; this.metrics = metrics;
    }

    @Transactional
    public void dispatch(String id) {
        var event = events.lock(id).orElseThrow();
        if ((event.status != EventStatus.PENDING && event.status != EventStatus.RETRYING && event.status != EventStatus.PUBLISHED)
                || event.nextAttemptAt.isAfter(Instant.now())) return;
        if (!distributed) { processor.deliverLocked(event); return; }
        try {
            transport.send(event.id);
            event.status = EventStatus.PUBLISHED;
            event.nextAttemptAt = Instant.now().plusSeconds(30); // Redrive if the consumer never confirms completion.
            event.lastError = null;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            event.fail("Broker publication failed");
            log.warn("outbox_publication_failed eventId={} attempt={} cause={}", event.id, event.attempts, e.getClass().getSimpleName());
            metrics.counter("billing.delivery.failures").increment();
        }
    }
}
