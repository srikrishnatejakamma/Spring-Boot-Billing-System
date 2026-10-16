package com.acme.worker;

import com.acme.billing.core.*;
import com.acme.billing.dto.*;
import com.acme.billing.integrations.DemoDestination;
import com.acme.worker.domain.*;
import com.acme.worker.persistence.*;
import com.acme.worker.service.*;
import io.github.resilience4j.ratelimiter.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.config.name=worker", "management.tracing.enabled=false"})
@ActiveProfiles("demo")
class InboxWorkflowTest {
    @Autowired InboxService service; @Autowired InboxRepository inbox; @Autowired CursorRepository cursors; @Autowired DeliveryJob jobs;
    @MockitoBean DemoDestination destination;
    @BeforeEach void clear() { inbox.deleteAll(); cursors.deleteAll(); reset(destination); }
    @Test void duplicateDeliveryHasOneEffectAndRemoteIoHasNoSqlTransaction() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main");
        doAnswer(invocation -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return null; }).when(destination).synchronize(any());
        jobs.run(event.change().eventId().toString()); service.accept(event, "main"); jobs.run(event.change().eventId().toString());
        verify(destination, times(1)).synchronize(any());
        assertThat(inbox.count()).isEqualTo(1); assertThat(inbox.findAll().getFirst().status).isEqualTo(InboxStatus.DELIVERED);
    }
    @Test void olderVersionsDoNotReachTheDestination() throws Exception {
        var id = UUID.randomUUID(); var old = envelope(id, 1); var newer = envelope(id, 2);
        service.accept(old, "main"); service.accept(newer, "main"); jobs.run(newer.change().eventId().toString()); jobs.run(old.change().eventId().toString());
        verify(destination, times(1)).synchronize(any());
        assertThat(cursors.findById(id.toString()).orElseThrow().syncedVersion).isEqualTo(2);
    }
    @Test void leasesSerializeAccountsAndLateCompletionsCannotOverwriteNewClaims() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main"); String id = event.change().eventId().toString();
        var first = service.claim(id); assertThat(service.claim(id)).isNull();
        var row = inbox.findById(id).orElseThrow(); row.leaseUntil = Instant.now().minusSeconds(1); inbox.save(row);
        var cursor = cursors.findById(row.accountId).orElseThrow(); cursor.leaseUntil = Instant.now().minusSeconds(1); cursors.save(cursor);
        var second = service.claim(id); assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(service.finish(first, null, false, false)).isFalse();
        assertThat(inbox.findById(id).orElseThrow().claimToken).isEqualTo(second.token());
        assertThat(service.finish(second, null, false, false)).isTrue();
    }
    @Test void exhaustedWorkRequiresANewReplayGeneration() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main"); String id = event.change().eventId().toString();
        doThrow(new IllegalStateException("unavailable")).when(destination).synchronize(any());
        for (int i = 0; i < 6; i++) { due(id); jobs.run(id); }
        var dead = inbox.findById(id).orElseThrow(); assertThat(dead.status).isEqualTo(InboxStatus.DEAD_LETTER); long previousRevision = dead.revision;
        service.accept(event, "main"); jobs.run(id); assertThat(inbox.findById(id).orElseThrow().status).isEqualTo(InboxStatus.DEAD_LETTER);
        reset(destination); service.accept(new EventEnvelope(1, event.change(), 1), "main"); jobs.run(id);
        var delivered = service.report(id); assertThat(delivered.generation()).isEqualTo(1); assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.revision()).isGreaterThan(previousRevision);
    }
    @Test void circuitAndRateDeferralsDoNotConsumeFailureAttempts() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main");
        doThrow(RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("test"))).when(destination).synchronize(any());
        jobs.run(event.change().eventId().toString()); var row = inbox.findAll().getFirst();
        assertThat(row.status).isEqualTo(InboxStatus.RETRYING); assertThat(row.attempts).isZero();
    }
    @Test void nonRetryableVendorRejectionsGoStraightToDeadLetter() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main");
        doThrow(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "Rejected", HttpHeaders.EMPTY, new byte[0], null)).when(destination).synchronize(any());
        jobs.run(event.change().eventId().toString()); var row = inbox.findAll().getFirst();
        assertThat(row.status).isEqualTo(InboxStatus.DEAD_LETTER); assertThat(row.attempts).isEqualTo(1);
    }
    @Test void reusedEventIdWithDifferentContentIsRejected() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main");
        var other = new AccountChanged(event.change().eventId(), "ACCOUNT_UPDATED", event.change().occurredAt(), event.change().account());
        assertThatThrownBy(() -> service.accept(new EventEnvelope(1, other, 0), "main")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reportAcknowledgementAndReplayKeepRevisionsMonotonic() throws Exception {
        var event = envelope(UUID.randomUUID(), 0); service.accept(event, "main"); String id = event.change().eventId().toString(); jobs.run(id);
        var report = service.report(id); service.reported(id, report.revision());
        assertThat(inbox.findById(id).orElseThrow().publishedRevision).isEqualTo(report.revision());
        service.accept(event, "main"); assertThat(inbox.findById(id).orElseThrow().publishedRevision).isLessThan(report.revision());
    }
    private void due(String id) { var row = inbox.findById(id).orElseThrow(); row.nextAttemptAt = Instant.now().minusSeconds(1); inbox.save(row); }
    private EventEnvelope envelope(UUID id, long version) {
        return new EventEnvelope(1, new AccountChanged(UUID.randomUUID(), "ACCOUNT_UPDATED", Instant.now(),
                new AccountSnapshot(id, "Worker test", "finance@worker.example", Plan.GROWTH, AccountStatus.ACTIVE, "149.00", "USD", version)), 0);
    }
}
