package com.acme.billing.audit;

import com.acme.platform.AccessPolicy;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditWriter {
    private final AuditRepository records;
    private final AccessPolicy access;

    public AuditWriter(AuditRepository records, AccessPolicy access) {
        this.records = records;
        this.access = access;
    }

    public void append(String action, String targetId, String justification) {
        String traceId = firstNonBlank(MDC.get("traceId"), MDC.get("correlationId"), UUID.randomUUID().toString());
        var row = new AuditRecord();
        row.id = UUID.randomUUID().toString();
        row.traceId = traceId;
        row.actor = access.actor();
        row.action = action;
        row.targetId = targetId;
        row.justification = bounded(justification == null || justification.isBlank() ? action : justification.trim());
        row.occurredAt = Instant.now();
        records.save(row);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return bounded(value);
        return "system";
    }

    private static String bounded(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
