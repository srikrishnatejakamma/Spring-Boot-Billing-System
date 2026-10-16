package com.acme.billing.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface AuditRepository extends Repository<AuditRecord, String> {
    <S extends AuditRecord> S save(S record);
    List<AuditRecord> findAll();
    long count();
    List<AuditRecord> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
