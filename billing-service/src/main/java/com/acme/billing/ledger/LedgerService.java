package com.acme.billing.ledger;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import com.acme.billing.dto.PostTransaction;
import com.acme.billing.events.AccountEventRecorder;
import com.acme.billing.audit.AuditWriter;
import com.acme.platform.AccessPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {
    private final AccountRepository accounts; private final LedgerRepository ledger; private final EntryRepository entries;
    private final AccountEventRecorder events; private final AccessPolicy access; private final ObjectMapper json;
    private final AuditWriter audit;
    public LedgerService(AccountRepository accounts, LedgerRepository ledger, EntryRepository entries,
                         AccountEventRecorder events, AccessPolicy access, ObjectMapper json, AuditWriter audit) {
        this.accounts = accounts; this.ledger = ledger; this.entries = entries; this.events = events; this.access = access; this.json = json; this.audit = audit;
    }
    @Transactional public LedgerViews.Transaction post(String id, PostTransaction input, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw bad("A valid Idempotency-Key is required");
        String fingerprint = fingerprint(id, input);
        var receipt = ledger.findByRequestKey(key);
        if (receipt.isPresent()) {
            if (!receipt.get().fingerprint.equals(fingerprint)) throw conflict("The posting key was used with different details");
            return LedgerViews.Transaction.of(receipt.get());
        }
        BigDecimal amount;
        try { amount = Money.positive(input.amount()); } catch (IllegalArgumentException exception) { throw bad(exception.getMessage()); }
        var account = accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!Objects.equals(account.version, input.expectedVersion())) throw conflict("The account changed. Refresh its current version");
        if (account.status == AccountStatus.CLOSED) throw conflict("Closed accounts cannot receive postings");
        if (input.kind() == TransactionKind.CHARGE && account.status != AccountStatus.ACTIVE) throw conflict("Only active accounts can be charged");
        if (input.kind() == TransactionKind.PAYMENT && amount.compareTo(account.outstandingBalance) > 0) throw conflict("Payment exceeds the outstanding balance");
        account.outstandingBalance = input.kind() == TransactionKind.CHARGE ? account.outstandingBalance.add(amount) : account.outstandingBalance.subtract(amount);
        if (account.outstandingBalance.precision() > 12) throw conflict("Account balance exceeds the supported monetary range");
        account.updatedAt = Instant.now(); accounts.flush();
        var transaction = new LedgerTransaction();
        transaction.id = UUID.randomUUID().toString(); transaction.accountId = id; transaction.kind = input.kind();
        transaction.amount = amount; transaction.description = input.description().trim(); transaction.actor = access.actor();
        transaction.requestKey = key; transaction.fingerprint = fingerprint; transaction.accountVersion = account.version; transaction.createdAt = Instant.now();
        ledger.saveAndFlush(transaction);
        var debit = entry(transaction.id, input.kind() == TransactionKind.CHARGE ? "AR_USD" : "CASH_USD", "DEBIT", amount);
        var credit = entry(transaction.id, input.kind() == TransactionKind.CHARGE ? "REVENUE_USD" : "AR_USD", "CREDIT", amount);
        entries.saveAll(List.of(debit, credit));
        events.record(account, "LEDGER_POSTED");
        audit.append("LEDGER_POSTED", transaction.id, input.description());
        return LedgerViews.Transaction.of(transaction);
    }
    @Transactional(readOnly = true) public List<LedgerViews.Transaction> history(String id, int limit) {
        if (limit < 1 || limit > 100) throw bad("limit must be between 1 and 100");
        if (!accounts.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return ledger.findByAccountIdOrderByCreatedAtDesc(id, PageRequest.of(0, limit)).stream().map(LedgerViews.Transaction::of).toList();
    }
    @Transactional(readOnly = true) public Map<String, List<LedgerViews.Transaction>> batch(Set<String> accountIds) {
        var result = new HashMap<String, List<LedgerViews.Transaction>>();
        for (var id : accountIds) result.put(id, new ArrayList<>());
        if (!accountIds.isEmpty()) for (var row : ledger.batchHistory(accountIds)) result.get(row.accountId).add(LedgerViews.Transaction.of(row));
        return result;
    }
    @Transactional(readOnly = true) public Map<String, List<LedgerViews.Entry>> batchEntries(Set<String> transactionIds) {
        var result = new HashMap<String, List<LedgerViews.Entry>>();
        for (var id : transactionIds) result.put(id, new ArrayList<>());
        if (!transactionIds.isEmpty()) for (var row : entries.findByTransactionIdIn(transactionIds)) result.get(row.transactionId).add(LedgerViews.Entry.of(row));
        return result;
    }
    private LedgerEntry entry(String transactionId, String account, String side, BigDecimal amount) {
        var entry = new LedgerEntry(); entry.id = UUID.randomUUID().toString(); entry.transactionId = transactionId;
        entry.ledgerAccountId = account; entry.side = side; entry.amount = amount; return entry;
    }
    private String fingerprint(String id, PostTransaction input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((id + ":" + json.writeValueAsString(input)).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Cannot encode posting receipt", exception); }
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
