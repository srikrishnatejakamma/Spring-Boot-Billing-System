package com.acme.worker.persistence;

import com.acme.worker.domain.AccountCursor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CursorRepository extends JpaRepository<AccountCursor, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AccountCursor c where c.accountId = :id")
    Optional<AccountCursor> lock(@Param("id") String id);
}
