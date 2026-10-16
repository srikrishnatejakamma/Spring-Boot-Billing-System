package com.acme.billing.integrations;

import com.acme.billing.dto.AccountChanged;

import java.util.List;

/** Applies a full-state event to independently idempotent destinations. */
public final class CompositeAccountDestination implements AccountDestination {
    private final List<AccountDestination> destinations;

    public CompositeAccountDestination(List<AccountDestination> destinations) {
        if (destinations.isEmpty()) throw new IllegalArgumentException("At least one destination is required");
        this.destinations = List.copyOf(destinations);
    }

    @Override public void synchronize(AccountChanged event) {
        for (var destination : destinations) destination.synchronize(event);
    }
}
