package com.acme.billing.account;

import com.acme.billing.dto.*;

import com.acme.billing.core.*;
import com.acme.billing.events.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final SyncRepository syncs;
    private final IdempotencyRepository keys;
    private final AccountEventRecorder recorder;
    private final ObjectMapper json;

    public AccountService(AccountRepository accounts, SyncRepository syncs, IdempotencyRepository keys,
                          AccountEventRecorder recorder, ObjectMapper json) {
        this.accounts = accounts; this.syncs = syncs; this.keys = keys; this.recorder = recorder; this.json = json;
    }

    @Transactional
    public BillingAccount create(AccountCommands.Create input, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide an Idempotency-Key of 8–100 letters, digits, underscores or hyphens");
        String fingerprint = fingerprint(input);
        var existing = keys.findById(key);
        if (existing.isPresent()) {
            if (!existing.get().fingerprint.equals(fingerprint))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This idempotency key was already used with different account details");
            return require(existing.get().accountId);
        }
        var account = new BillingAccount();
        account.id = UUID.randomUUID().toString();
        account.company = input.company().trim();
        account.billingEmail = input.billingEmail().trim().toLowerCase(Locale.ROOT);
        account.plan = input.plan();
        account.monthlyRate = input.plan().monthlyRate();
        account.status = AccountStatus.ACTIVE;
        account.createdAt = account.updatedAt = Instant.now();
        accounts.saveAndFlush(account);
        syncs.save(new AccountSync(account.id));
        keys.saveAndFlush(new IdempotencyRecord(key, fingerprint, account.id));
        recorder.record(account, "ACCOUNT_CREATED");
        return account;
    }

    @Transactional
    public BillingAccount update(String id, AccountCommands.Update input) {
        var account = require(id);
        if (!Objects.equals(account.version, input.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account changed. Refresh and try again with its current version");
        if (account.status == AccountStatus.CLOSED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed accounts are read-only");
        if (input.status() == AccountStatus.CLOSED && account.outstandingBalance.signum() != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Settle the outstanding balance before closing the account");
        account.company = input.company().trim();
        account.billingEmail = input.billingEmail().trim().toLowerCase(Locale.ROOT);
        account.plan = input.plan();
        account.monthlyRate = input.plan().monthlyRate();
        account.status = input.status();
        account.updatedAt = Instant.now();
        accounts.flush(); // @Version is assigned before the full-state event is serialized.
        recorder.record(account, "ACCOUNT_UPDATED");
        return account;
    }

    public BillingAccount require(String id) {
        return accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private String fingerprint(AccountCommands.Create input) {
        try {
            String normalized = json.writeValueAsString(new AccountCommands.Create(input.company().trim(),
                    input.billingEmail().trim().toLowerCase(Locale.ROOT), input.plan()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
