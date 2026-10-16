package com.acme.billing.events;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> lock(@Param("id") String id);
    @Query("select e.id from OutboxEvent e where e.status in :statuses and e.nextAttemptAt <= :now order by e.createdAt")
    List<String> due(@Param("statuses") Collection<EventStatus> statuses, @Param("now") Instant now, Pageable page);
    List<OutboxEvent> findTop100ByOrderByCreatedAtDesc();
    List<OutboxEvent> findByAccountIdOrderByCreatedAtDesc(String accountId);
    long countByStatus(EventStatus status);
}
