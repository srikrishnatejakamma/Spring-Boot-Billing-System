package com.acme.billing.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "billing.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private final OutboxRepository events;
    private final OutboxDispatcher dispatcher;
    public OutboxScheduler(OutboxRepository events, OutboxDispatcher dispatcher) { this.events = events; this.dispatcher = dispatcher; }

    @Scheduled(fixedDelayString = "${billing.scheduler.delay:2000}")
    public void dispatch() {
        for (String id : events.due(List.of(EventStatus.PENDING, EventStatus.RETRYING, EventStatus.PUBLISHED), Instant.now(), PageRequest.of(0, 25))) {
            try { dispatcher.dispatch(id); }
            catch (Exception e) { log.warn("outbox_dispatch_deferred eventId={} cause={}", id, e.getClass().getSimpleName()); }
        }
    }
}
