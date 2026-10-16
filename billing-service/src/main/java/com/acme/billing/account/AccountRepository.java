package com.acme.billing.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<BillingAccount, String> {}
