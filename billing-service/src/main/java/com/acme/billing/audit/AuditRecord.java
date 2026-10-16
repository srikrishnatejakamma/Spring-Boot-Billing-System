package com.acme.billing.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "billing_audit_log")
public class AuditRecord {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 100) public String traceId;
    @Column(nullable = false, length = 255) public String actor;
    @Column(nullable = false, length = 50) public String action;
    @Column(nullable = false, length = 100) public String targetId;
    @Column(nullable = false, length = 500) public String justification;
    @Column(nullable = false) public Instant occurredAt;

    protected AuditRecord() {}
}
