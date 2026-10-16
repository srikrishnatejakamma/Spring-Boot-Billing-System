package com.acme.billing.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    public record AuditView(String id, String traceId, String actor, String action, String targetId,
                            String justification, Instant occurredAt) {}

    private final AuditRepository records;

    public AuditController(AuditRepository records) { this.records = records; }

    @GetMapping
    @PreAuthorize("@accessPolicy.allows('billing.audit')")
    public List<AuditView> list(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        return records.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit)).stream()
                .map(row -> new AuditView(row.id, row.traceId, row.actor, row.action, row.targetId, row.justification, row.occurredAt))
                .toList();
    }
}
