package com.acme.billing.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.acme.billing.core.AccountStatus;
import java.math.BigDecimal;

public interface AccountRepository extends JpaRepository<BillingAccount, String> {
    long countByStatus(AccountStatus status);
    @Query("select coalesce(sum(a.monthlyRate), 0) from BillingAccount a where a.status = com.acme.billing.core.AccountStatus.ACTIVE")
    BigDecimal recurringRevenue();
    @Query("select count(a) from BillingAccount a, AccountSync s where s.accountId = a.id and s.syncedVersion >= a.version")
    long countSynchronized();
}
