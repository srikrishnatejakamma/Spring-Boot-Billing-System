package com.acme.worker.service;

import com.acme.billing.integrations.AccountDestination;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class DeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(DeliveryJob.class);
    private final InboxService inbox;
    private final AccountDestination destination;
    private final MeterRegistry metrics;
    private final ObservationRegistry observations;
    public DeliveryJob(InboxService inbox, AccountDestination destination, MeterRegistry metrics, ObservationRegistry observations) {
        this.inbox = inbox; this.destination = destination; this.metrics = metrics; this.observations = observations;
    }
    public void run(String id) throws Exception {
        var claim = inbox.claim(id);
        if (claim == null) return;
        try {
            // Deliberately outside the SQL transaction and all database row locks.
            Observation.createNotStarted("billing.worker.synchronize", observations)
                    .observe(() -> destination.synchronize(claim.change()));
            if (inbox.finish(claim, null, false, false)) metrics.counter("billing.delivery.success").increment();
        } catch (CallNotPermittedException | RequestNotPermitted exception) {
            inbox.finish(claim, "Downstream circuit or rate budget requires deferral", false, true);
            metrics.counter("billing.delivery.deferred").increment();
        } catch (Exception exception) {
            boolean permanent = exception instanceof HttpClientErrorException client
                    && client.getStatusCode().value() != 408 && client.getStatusCode().value() != 429;
            inbox.finish(claim, permanent ? "Destination rejected the event contract" : "Destination temporarily unavailable", permanent, false);
            metrics.counter("billing.delivery.failures").increment();
            log.warn("worker_delivery_failed eventId={} cause={}", id, exception.getClass().getSimpleName());
        }
    }
}
