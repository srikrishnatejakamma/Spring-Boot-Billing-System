package com.acme.billing.web;

import com.acme.platform.AccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RuntimeConfigurationController {
    private final AccessPolicy access;
    private final String issuer;
    private final boolean ledger;
    public RuntimeConfigurationController(AccessPolicy access, @Value("${security.jwt.issuer}") String issuer,
                                           @Value("${billing.ledger.enabled:false}") boolean ledger) {
        this.access = access; this.issuer = issuer; this.ledger = ledger;
    }
    @GetMapping("/api/config") public Map<String, Object> configuration() {
        return Map.of("demo", !access.secured(), "issuer", issuer, "clientId", "billing-console",
                "scope", "openid profile billing.read billing.write billing.pii ops.read events.retry", "ledgerEnabled", ledger);
    }
}
