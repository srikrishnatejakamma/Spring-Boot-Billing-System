package com.acme.billing.web;

import com.acme.billing.dto.*;

import com.acme.billing.account.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;
    private final QueryService queries;
    public AccountController(AccountService service, QueryService queries) { this.service = service; this.queries = queries; }
    @GetMapping public List<Views.Account> list() { return queries.accounts(); }
    @GetMapping("/{id}") public Views.Account get(@PathVariable String id) { return queries.account(id); }
    @PostMapping public ResponseEntity<Views.Account> create(@Valid @RequestBody AccountCommands.Create input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var account = service.create(input, key);
        return ResponseEntity.created(URI.create("/api/accounts/" + account.id)).body(queries.account(account.id));
    }
    @PutMapping("/{id}") public Views.Account update(@PathVariable String id, @Valid @RequestBody AccountCommands.Update input) {
        service.update(id, input); return queries.account(id);
    }
}
