package com.acme.billing.web;

import com.acme.billing.events.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
public class OperationsService {
    private final OutboxRepository events;
    public OperationsService(OutboxRepository events) { this.events = events; }
    @Transactional public Views.Event retry(String id) {
        var event = events.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (event.status != EventStatus.RETRYING && event.status != EventStatus.DEAD_LETTER)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only retrying or dead-letter events can be retried");
        event.status = EventStatus.PENDING;
        event.attempts = 0;
        event.generation++;
        event.nextAttemptAt = Instant.now();
        return Views.Event.of(event);
    }
}
