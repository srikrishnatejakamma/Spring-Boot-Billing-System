package com.acme.worker.service;

import com.acme.billing.dto.*;
import com.acme.worker.domain.*;
import com.acme.worker.persistence.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class InboxService {
    public record Claim(String id, String token, int generation, AccountChanged change) {}
    private final InboxRepository inbox;
    private final CursorRepository cursors;
    private final ObjectMapper json;
    private final long leaseSeconds;
    public InboxService(InboxRepository inbox, CursorRepository cursors, ObjectMapper json,
                        @Value("${worker.lease-seconds:30}") long leaseSeconds) {
        this.inbox = inbox; this.cursors = cursors; this.json = json; this.leaseSeconds = leaseSeconds;
    }

    @Transactional public void accept(EventEnvelope envelope, String cohort) throws JsonProcessingException {
        String id = envelope.change().eventId().toString();
        String payload = json.writeValueAsString(envelope.change());
        var existing = inbox.lock(id);
        if (existing.isPresent()) {
            var message = existing.get();
            if (!message.payload.equals(payload)) throw new IllegalArgumentException("Event ID was reused with different content");
            if (envelope.generation() > message.generation) message.replay(envelope.generation());
            if (message.status == InboxStatus.DELIVERED || message.status == InboxStatus.DEAD_LETTER)
                message.publishedRevision = Math.min(message.publishedRevision, message.revision - 1);
            return;
        }
        if (!cursors.existsById(envelope.change().account().id().toString()))
            cursors.saveAndFlush(new AccountCursor(envelope.change().account().id().toString()));
        inbox.saveAndFlush(new InboxMessage(envelope, payload, cohort));
    }

    @Transactional public Claim claim(String id) throws JsonProcessingException {
        var message = inbox.lock(id).orElseThrow();
        Instant now = Instant.now();
        if (message.status == InboxStatus.DELIVERED || message.status == InboxStatus.DEAD_LETTER
                || (message.status != InboxStatus.PROCESSING && message.nextAttemptAt.isAfter(now))
                || (message.status == InboxStatus.PROCESSING && message.leaseUntil != null && message.leaseUntil.isAfter(now))) return null;
        var cursor = cursors.lock(message.accountId).orElseThrow();
        if (cursor.syncedVersion >= message.accountVersion) { message.complete(); return null; }
        if (!cursor.available(now)) return null;
        String token = UUID.randomUUID().toString();
        message.status = InboxStatus.PROCESSING; message.claimToken = token; message.leaseUntil = now.plusSeconds(leaseSeconds);
        cursor.leaseToken = token; cursor.leaseUntil = message.leaseUntil;
        return new Claim(id, token, message.generation, json.readValue(message.payload, AccountChanged.class));
    }

    @Transactional public boolean finish(Claim claim, String error, boolean permanent, boolean deferred) {
        var message = inbox.lock(claim.id()).orElseThrow();
        var cursor = cursors.lock(message.accountId).orElseThrow();
        if (!Objects.equals(message.claimToken, claim.token()) || message.generation != claim.generation()) {
            if (Objects.equals(cursor.leaseToken, claim.token())) cursor.release();
            return false;
        }
        if (error == null) {
            cursor.syncedVersion = Math.max(cursor.syncedVersion, message.accountVersion); message.complete();
        } else if (deferred) message.defer(error);
        else message.fail(error, permanent);
        if (Objects.equals(cursor.leaseToken, claim.token())) cursor.release();
        return true;
    }

    @Transactional(readOnly = true) public DeliveryReport report(String id) { return inbox.findById(id).orElseThrow().report(); }
    @Transactional public void reported(String id, long revision) {
        var message = inbox.lock(id).orElseThrow(); message.publishedRevision = Math.max(message.publishedRevision, revision);
    }
}
