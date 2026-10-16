package com.acme.billing.config;

import com.acme.billing.events.*;
import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.*;

@Configuration
public class BillingMetrics {
    @Bean Object outboxGauges(MeterRegistry registry, OutboxRepository events) {
        Gauge.builder("billing.outbox.pending", events, r -> r.countByStatus(EventStatus.PENDING)
                + r.countByStatus(EventStatus.PUBLISHED) + r.countByStatus(EventStatus.RETRYING)).register(registry);
        Gauge.builder("billing.outbox.dead", events, r -> r.countByStatus(EventStatus.DEAD_LETTER)).register(registry);
        return new Object();
    }
}
