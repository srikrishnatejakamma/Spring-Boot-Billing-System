package com.acme.billing.config;

import com.acme.billing.dto.*;

import com.acme.billing.events.*;
import com.acme.billing.integrations.DemoDestination;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Comparator;

/** Rehydrate the ephemeral demo projection without changing durable receipts or sending HTTP. */
@Component @Profile("demo") @Order(0)
public class DemoProjectionRecovery implements CommandLineRunner {
    private final OutboxRepository events;
    private final DemoDestination destination;
    private final ObjectMapper json;
    public DemoProjectionRecovery(OutboxRepository events, DemoDestination destination, ObjectMapper json) {
        this.events = events; this.destination = destination; this.json = json;
    }
    @Override public void run(String... args) throws Exception {
        for (var event : events.findAll().stream().filter(e -> e.status == EventStatus.DELIVERED)
                .sorted(Comparator.comparingLong(e -> e.accountVersion)).toList()) {
            destination.synchronize(json.readValue(event.payload, AccountChanged.class));
        }
    }
}
