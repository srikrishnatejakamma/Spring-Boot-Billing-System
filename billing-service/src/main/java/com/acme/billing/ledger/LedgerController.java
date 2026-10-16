package com.acme.billing.ledger;

import com.acme.billing.dto.PostTransaction;
import com.acme.billing.web.*;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/accounts/{id}/transactions")
@ConditionalOnProperty(name = "billing.ledger.enabled", havingValue = "true")
public class LedgerController {
    private final LedgerService ledger; private final QueryService queries;
    public LedgerController(LedgerService ledger, QueryService queries) { this.ledger = ledger; this.queries = queries; }
    public record Posted(LedgerViews.Transaction transaction, Views.Account account) {}
    @GetMapping public List<LedgerViews.Transaction> history(@PathVariable String id, @RequestParam(defaultValue = "50") int limit) { return ledger.history(id, limit); }
    @PostMapping @PreAuthorize("@accessPolicy.allows('billing.write')")
    public Posted post(@PathVariable String id, @Valid @RequestBody PostTransaction input,
                       @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var transaction = ledger.post(id, input, key); return new Posted(transaction, queries.account(id));
    }
}
