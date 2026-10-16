package com.acme.billing.events;

import com.acme.billing.account.BillingAccount;
import com.acme.billing.dto.AccountChanged;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountEventRecorder {
    private final OutboxRepository outbox; private final ObjectMapper json; private final String route;
    public AccountEventRecorder(OutboxRepository outbox, ObjectMapper json,
                                @Value("${billing.delivery.routing-key:account.changed}") String route) {
        this.outbox = outbox; this.json = json; this.route = route;
        if (!route.matches("[a-z][a-z0-9.-]{1,79}")) throw new IllegalArgumentException("Invalid delivery routing key");
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BillingAccount account, String type) {
        try {
            var event = new AccountChanged(UUID.randomUUID(), type, Instant.now(), account.snapshot());
            var row = new OutboxEvent(event, json.writeValueAsString(event)); row.routingKey = route; outbox.save(row);
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot encode account change", exception); }
    }
}
