package com.acme.billing;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import com.acme.billing.dto.*;
import com.acme.billing.events.*;
import com.acme.billing.ledger.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ledger;MODE=MySQL;DB_CLOSE_DELAY=-1", "billing.seed=false", "billing.scheduler.enabled=false"})
@AutoConfigureMockMvc
class LedgerWorkflowTest {
    @Autowired AccountService service; @Autowired AccountRepository accounts; @Autowired LedgerService ledger;
    @Autowired LedgerRepository transactions; @Autowired EntryRepository entries; @Autowired OutboxRepository events;
    @Autowired IdempotencyRepository keys; @Autowired SyncRepository syncs; @Autowired ObjectMapper json;
    @Autowired MockMvc mvc; @Autowired EntityManagerFactory factory;
    @BeforeEach void clear() {
        entries.deleteAll(); transactions.deleteAll(); events.deleteAll(); keys.deleteAll(); syncs.deleteAll(); accounts.deleteAll();
    }
    @Test void chargeAndPaymentProduceBalancedEntriesAndExactReceivables() throws Exception {
        var account = account();
        var charge = ledger.post(account.id, posting(TransactionKind.CHARGE, "49.99", 0), "charge-key-01");
        var payment = ledger.post(account.id, posting(TransactionKind.PAYMENT, "0.29", 1), "payment-key-01");
        var rows = entries.findByTransactionIdIn(List.of(charge.id(), payment.id()));
        for (String id : List.of(charge.id(), payment.id())) {
            BigDecimal debit = rows.stream().filter(e -> e.transactionId.equals(id) && e.side.equals("DEBIT")).map(e -> e.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = rows.stream().filter(e -> e.transactionId.equals(id) && e.side.equals("CREDIT")).map(e -> e.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(debit).isEqualByComparingTo(credit);
        }
        assertThat(rows.stream().filter(e -> e.transactionId.equals(charge.id()) && e.side.equals("DEBIT")).findFirst().orElseThrow().ledgerAccountId).isEqualTo("AR_USD");
        assertThat(rows.stream().filter(e -> e.transactionId.equals(payment.id()) && e.side.equals("DEBIT")).findFirst().orElseThrow().ledgerAccountId).isEqualTo("CASH_USD");
        assertThat(accounts.findById(account.id).orElseThrow().outstandingBalance).isEqualByComparingTo("49.70");
        var latest = events.findByAccountIdOrderByCreatedAtDesc(account.id).getFirst();
        assertThat(json.readValue(latest.payload, AccountChanged.class).account().outstandingBalance()).isEqualTo("49.70");
        assertThat(events.count()).isEqualTo(3);
    }
    @Test void repeatingAPostingKeyDoesNotApplyTheChargeTwice() {
        var account = account(); var input = posting(TransactionKind.CHARGE, "10.00", 0);
        var first = ledger.post(account.id, input, "same-charge-key"); var second = ledger.post(account.id, input, "same-charge-key");
        assertThat(first.id()).isEqualTo(second.id()); assertThat(transactions.count()).isEqualTo(1); assertThat(entries.count()).isEqualTo(2);
        assertThat(accounts.findById(account.id).orElseThrow().outstandingBalance).isEqualByComparingTo("10.00");
    }
    @Test void keyMismatchAndStaleVersionCannotCreateExtraHistory() {
        var account = account(); ledger.post(account.id, posting(TransactionKind.CHARGE, "10.00", 0), "first-charge-key");
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "11.00", 0), "first-charge-key")).hasMessageContaining("409");
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "11.00", 0), "second-charge-key")).hasMessageContaining("409");
        assertThat(transactions.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(2);
    }
    @Test void rejectsOverpaymentFractionalCentsAndInvalidKeysAtomically() {
        var account = account();
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.PAYMENT, "1.00", 0), "overpayment-key")).hasMessageContaining("409");
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "0.001", 0), "fractional-cent-key")).hasMessageContaining("400");
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "1.00", 0), "bad")).hasMessageContaining("400");
        assertThat(transactions.count()).isZero(); assertThat(entries.count()).isZero(); assertThat(events.count()).isEqualTo(1);
    }
    @Test void balanceMustBeSettledBeforeClosureAndSuspensionStillAllowsPayment() {
        var account = account(); ledger.post(account.id, posting(TransactionKind.CHARGE, "10.00", 0), "closure-charge-key");
        assertThatThrownBy(() -> service.update(account.id, new AccountCommands.Update(account.company, account.billingEmail, account.plan, AccountStatus.CLOSED, 1L))).hasMessageContaining("409");
        service.update(account.id, new AccountCommands.Update(account.company, account.billingEmail, account.plan, AccountStatus.SUSPENDED, 1L));
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "1.00", 2), "suspended-charge-key")).hasMessageContaining("409");
        ledger.post(account.id, posting(TransactionKind.PAYMENT, "10.00", 2), "settlement-payment-key");
        service.update(account.id, new AccountCommands.Update(account.company, account.billingEmail, account.plan, AccountStatus.CLOSED, 3L));
        assertThatThrownBy(() -> ledger.post(account.id, posting(TransactionKind.CHARGE, "1.00", 4), "closed-charge-key")).hasMessageContaining("409");
    }
    @Test void competingChargesCommitOnlyOneBalanceVersion() throws Exception {
        var account = account(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> { start.await(); try { ledger.post(account.id, posting(TransactionKind.CHARGE, "10.00", 0), "concurrent-charge-1"); return true; } catch (RuntimeException exception) { return false; } };
            Callable<Boolean> second = () -> { start.await(); try { ledger.post(account.id, posting(TransactionKind.CHARGE, "11.00", 0), "concurrent-charge-2"); return true; } catch (RuntimeException exception) { return false; } };
            var one = executor.submit(first); var two = executor.submit(second); start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(transactions.count()).isEqualTo(1); assertThat(entries.count()).isEqualTo(2); assertThat(events.count()).isEqualTo(2);
    }
    @Test void nestedGraphqlHistoryAndEntriesAreBatched() throws Exception {
        for (int i = 0; i < 6; i++) { var account = account(); ledger.post(account.id, posting(TransactionKind.CHARGE, "1.00", 0), "batch-charge-key-" + i); }
        var statistics = factory.unwrap(SessionFactory.class).getStatistics(); statistics.setStatisticsEnabled(true); statistics.clear();
        mvc.perform(post("/graphql").contentType("application/json").content("{\"query\":\"{ accounts { id transactions { id amount entries { side amount } } } }\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("errors").doesNotExist()).andExpect(jsonPath("data.accounts.length()").value(6))
                .andExpect(jsonPath("data.accounts[0].transactions[0].entries.length()").value(2));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }
    @Test void restPostingReturnsPersistedActorAndUpdatedAccount() throws Exception {
        var account = account();
        mvc.perform(post("/api/accounts/" + account.id + "/transactions").header("Idempotency-Key", "rest-charge-key-01")
                .contentType("application/json").content(json.writeValueAsString(posting(TransactionKind.CHARGE, "25.50", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("transaction.actor").value("demo-operator"))
                .andExpect(jsonPath("account.outstandingBalance").value("25.50")).andExpect(jsonPath("account.version").value(1));
    }
    private BillingAccount account() { return service.create(new AccountCommands.Create("Ledger account", "finance@ledger.example", Plan.GROWTH), "new-ledger-" + UUID.randomUUID()); }
    private PostTransaction posting(TransactionKind kind, String amount, long version) { return new PostTransaction(kind, amount, "Synthetic test posting", version); }
}
