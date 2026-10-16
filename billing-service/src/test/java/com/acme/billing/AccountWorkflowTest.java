package com.acme.billing;

import com.acme.billing.dto.*;

import com.acme.billing.account.*;
import com.acme.billing.audit.*;
import com.acme.billing.core.*;
import com.acme.billing.events.*;
import com.acme.billing.integrations.DemoDestination;
import com.acme.billing.web.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "billing.scheduler.enabled=false", "billing.seed=false" })
@AutoConfigureMockMvc
class AccountWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired OutboxRepository events;
    @Autowired SyncRepository syncs;
    @Autowired IdempotencyRepository keys;
    @Autowired AuditRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService service;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired DeliveryProcessor processor;
    @Autowired DemoDestination destination;
    @Autowired OperationsService operations;
    @Autowired QueryService queries;

    @BeforeEach void clear() {
        jdbc.update("DELETE FROM billing_audit_log"); events.deleteAll(); keys.deleteAll(); syncs.deleteAll(); accounts.deleteAll(); destination.setAvailable(true);
    }

    @Test void onboardingIsIdempotentAndWritesOneTransactionalEvent() throws Exception {
        String body = "{\"company\":\"Acme\",\"billingEmail\":\"billing@acme.example\",\"plan\":\"GROWTH\",\"justification\":\"Customer onboarding request\"}";
        String first = mvc.perform(post("/api/accounts").header("Idempotency-Key", "onboard-key-01")
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/accounts").header("Idempotency-Key", "onboard-key-01")
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first).get("id")).isEqualTo(json.readTree(second).get("id"));
        assertThat(accounts.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(1);
        assertThat(events.findAll().getFirst().accountVersion).isZero();
        var auditRecord = audit.findAll().getFirst();
        assertThat(auditRecord.action).isEqualTo("ACCOUNT_CREATED");
        assertThat(auditRecord.actor).isEqualTo("demo-operator");
        assertThat(auditRecord.traceId).isNotBlank();
        assertThat(auditRecord.justification).isEqualTo("Customer onboarding request");
        assertThat(auditRecord.occurredAt).isNotNull();
    }

    @Test void reusingAKeyForDifferentInputIsRejectedWithoutNewWrites() throws Exception {
        var account = create();
        mvc.perform(post("/api/accounts").header("Idempotency-Key", "test-key-01").contentType("application/json")
                .content("{\"company\":\"Another\",\"billingEmail\":\"other@acme.example\",\"plan\":\"STARTER\"}"))
                .andExpect(status().isConflict());
        assertThat(accounts.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(1);
    }

    @Test void staleVersionReturns409AndDoesNotEmitAnotherEvent() throws Exception {
        var account = create();
        String body = json.writeValueAsString(new AccountCommands.Update("Acme", account.billingEmail, Plan.ENTERPRISE, AccountStatus.ACTIVE, 0L, "Approved plan change"));
        mvc.perform(put("/api/accounts/" + account.id).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("version").value(1)).andExpect(jsonPath("monthlyRate").value("499.00"));
        mvc.perform(put("/api/accounts/" + account.id).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(events.count()).isEqualTo(2);
        assertThat(audit.findAllByOrderByOccurredAtDesc(org.springframework.data.domain.PageRequest.of(0, 10)))
                .extracting(row -> row.justification).contains("Customer onboarding request", "Approved plan change");
        assertThat(audit.count()).isEqualTo(2);
    }

    @Test void concurrentUpdatesCommitOnlyOneVersion() throws Exception {
        var account = create();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var input = new AccountCommands.Update("Concurrent", account.billingEmail, Plan.ENTERPRISE, AccountStatus.ACTIVE, 0L);
            Callable<Boolean> action = () -> { start.await(); try { service.update(account.id, input); return true; } catch (RuntimeException e) { return false; } };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(accounts.findById(account.id).orElseThrow().version).isEqualTo(1);
        assertThat(events.count()).isEqualTo(2);
        assertThat(audit.count()).isEqualTo(2);
    }

    @Test void platformFailureIsRetriedWithoutLosingTheAccount() {
        var account = create(); var event = events.findAll().getFirst();
        destination.setAvailable(false); dispatcher.dispatch(event.id);
        var failed = events.findById(event.id).orElseThrow();
        assertThat(failed.status).isEqualTo(EventStatus.RETRYING); assertThat(failed.attempts).isEqualTo(1);
        assertThat(accounts.existsById(account.id)).isTrue();
        destination.setAvailable(true); operations.retry(event.id); dispatcher.dispatch(event.id);
        processor.receive(event.id); // duplicate broker delivery
        assertThat(events.findById(event.id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
        assertThat(syncs.findById(account.id).orElseThrow().syncedVersion).isZero();
    }

    @Test void oldEventsCannotRollBackTheProjection() {
        var account = create(); var old = events.findAll().getFirst();
        service.update(account.id, new AccountCommands.Update("Upgraded", account.billingEmail, Plan.ENTERPRISE, AccountStatus.ACTIVE, 0L));
        var latest = events.findByAccountIdOrderByCreatedAtDesc(account.id).getFirst();
        dispatcher.dispatch(latest.id); dispatcher.dispatch(old.id);
        assertThat(destination.snapshots().get(UUID.fromString(account.id)).plan()).isEqualTo(Plan.ENTERPRISE);
        assertThat(syncs.findById(account.id).orElseThrow().syncedVersion).isEqualTo(1);
        assertThat(events.findById(old.id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
    }

    @Test void sixFailuresReachDeadLetterAndCanBeReplayed() {
        create(); var id = events.findAll().getFirst().id; destination.setAvailable(false);
        for (int i = 0; i < 6; i++) {
            var event = events.findById(id).orElseThrow(); event.nextAttemptAt = java.time.Instant.now().minusSeconds(1); events.save(event);
            dispatcher.dispatch(id);
        }
        assertThat(events.findById(id).orElseThrow().status).isEqualTo(EventStatus.DEAD_LETTER);
        destination.setAvailable(true); operations.retry(id); dispatcher.dispatch(id);
        assertThat(events.findById(id).orElseThrow().status).isEqualTo(EventStatus.DELIVERED);
    }

    @Test void closedAccountsAreReadOnlyAndExcludedFromRevenue() {
        var account = create();
        service.update(account.id, new AccountCommands.Update("Acme", account.billingEmail, Plan.GROWTH, AccountStatus.CLOSED, 0L));
        assertThat(queries.overview().monthlyRevenue()).isEqualTo("0.00");
        assertThatThrownBy(() -> service.update(account.id, new AccountCommands.Update("Reopen", account.billingEmail, Plan.GROWTH, AccountStatus.ACTIVE, 1L)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void validationRejectsInvalidInputBeforeWriting() throws Exception {
        mvc.perform(post("/api/accounts").header("Idempotency-Key", "validation-01").contentType("application/json")
                .content("{\"company\":\"\",\"billingEmail\":\"invalid\",\"plan\":\"GROWTH\"}"))
                .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero(); assertThat(events.count()).isZero();
    }

    @Test void dgsExposesAccountsAndOverview() throws Exception {
        create();
        mvc.perform(post("/graphql").contentType("application/json").content("{\"query\":\"{ accounts { company plan status monthlyRate version createdAt updatedAt syncStatus } events { status type createdAt nextAttemptAt } overview { totalAccounts monthlyRevenue } }\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("errors").doesNotExist())
                .andExpect(jsonPath("data.accounts[0].company").value("Acme"))
                .andExpect(jsonPath("data.overview.monthlyRevenue").value("149.00"));
    }
    private BillingAccount create() { return service.create(new AccountCommands.Create("Acme", "billing@acme.example", Plan.GROWTH), "test-key-01"); }
}
