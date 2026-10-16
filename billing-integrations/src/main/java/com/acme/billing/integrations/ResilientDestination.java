package com.acme.billing.integrations;

import com.acme.billing.dto.AccountChanged;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

/** The fallback is durable deferral in the caller; this adapter never fabricates success. */
public final class ResilientDestination implements AccountDestination {
    private final AccountDestination delegate;
    private final CircuitBreaker circuit;
    private final RateLimiter rate;
    public ResilientDestination(AccountDestination delegate, CircuitBreaker circuit, RateLimiter rate) {
        this.delegate = delegate; this.circuit = circuit; this.rate = rate;
    }
    @Override public void synchronize(AccountChanged event) {
        if (!rate.acquirePermission()) throw RequestNotPermitted.createRequestNotPermitted(rate);
        circuit.executeRunnable(() -> delegate.synchronize(event));
    }
    public CircuitBreaker.State state() { return circuit.getState(); }
}
