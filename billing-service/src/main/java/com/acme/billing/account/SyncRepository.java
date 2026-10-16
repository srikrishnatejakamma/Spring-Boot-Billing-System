package com.acme.billing.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SyncRepository extends JpaRepository<AccountSync, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AccountSync s where s.accountId = :id")
    Optional<AccountSync> lock(@Param("id") String id);
}
