package com.acme.worker.persistence;

import com.acme.worker.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface InboxRepository extends JpaRepository<InboxMessage, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from InboxMessage i where i.id = :id")
    Optional<InboxMessage> lock(@Param("id") String id);
    @Query("select i.id from InboxMessage i where i.cohort = :cohort and ((i.status in :statuses and i.nextAttemptAt <= :now) or (i.status = com.acme.worker.domain.InboxStatus.PROCESSING and i.leaseUntil <= :now)) order by i.receivedAt")
    List<String> due(@Param("cohort") String cohort, @Param("statuses") Collection<InboxStatus> statuses, @Param("now") Instant now, Pageable page);
    @Query("select i.id from InboxMessage i where i.cohort = :cohort and i.revision > i.publishedRevision and i.status in :statuses order by i.receivedAt")
    List<String> unreported(@Param("cohort") String cohort, @Param("statuses") Collection<InboxStatus> statuses, Pageable page);
    List<InboxMessage> findTop100ByCohortOrderByReceivedAtDesc(String cohort);
    long countByStatus(InboxStatus status);
}
