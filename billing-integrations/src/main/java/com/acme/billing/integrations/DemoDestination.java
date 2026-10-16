package com.acme.billing.integrations;

import com.acme.billing.core.AccountChanged;
import com.acme.billing.core.AccountSnapshot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deliberately explicit simulator for running without Docker or third-party credentials. */
public class DemoDestination implements AccountDestination {
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final Map<UUID, AccountSnapshot> projection = new ConcurrentHashMap<>();

    public void setAvailable(boolean value) { available.set(value); }
    public boolean isAvailable() { return available.get(); }
    public Map<UUID, AccountSnapshot> snapshots() { return Map.copyOf(projection); }

    @Override public void synchronize(AccountChanged event) {
        if (!available.get()) throw new IllegalStateException("Demo billing platform is unavailable");
        projection.compute(event.account().id(), (id, previous) ->
                previous == null || previous.version() < event.account().version() ? event.account() : previous);
    }
}
