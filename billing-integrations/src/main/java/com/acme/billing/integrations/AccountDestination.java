package com.acme.billing.integrations;

import com.acme.billing.dto.*;


public interface AccountDestination {
    void synchronize(AccountChanged event);
}
