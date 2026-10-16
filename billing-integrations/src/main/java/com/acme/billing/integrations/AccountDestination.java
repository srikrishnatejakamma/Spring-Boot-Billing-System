package com.acme.billing.integrations;

import com.acme.billing.core.AccountChanged;

public interface AccountDestination {
    void synchronize(AccountChanged event);
}
