package com.acme.billing.integrations;

import com.acme.billing.core.*;
import com.acme.billing.dto.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.ratelimiter.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class ResilientDestinationTest {
    @Test void circuitPreventsCallsDuringFailureAndRecoversWithAProbe() {
        var calls = new AtomicInteger(); var available = new AtomicBoolean(false);
        AccountDestination raw = event -> { calls.incrementAndGet(); if (!available.get()) throw new IllegalStateException("unavailable"); };
        var circuit = CircuitBreaker.of("test", CircuitBreakerConfig.custom().slidingWindowSize(2).minimumNumberOfCalls(2)
                .permittedNumberOfCallsInHalfOpenState(1).build());
        var adapter = new ResilientDestination(raw, circuit, RateLimiter.ofDefaults("test"));
        assertThatThrownBy(() -> adapter.synchronize(event())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.synchronize(event())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.synchronize(event())).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls.get()).isEqualTo(2);
        available.set(true); circuit.transitionToHalfOpenState(); adapter.synchronize(event());
        assertThat(adapter.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
    @Test void exhaustedRateBudgetDoesNotCallTheVendor() {
        var calls = new AtomicInteger();
        var limiter = RateLimiter.of("limited", RateLimiterConfig.custom().limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofHours(1)).timeoutDuration(Duration.ZERO).build());
        var adapter = new ResilientDestination(event -> calls.incrementAndGet(), CircuitBreaker.ofDefaults("test"), limiter);
        adapter.synchronize(event());
        assertThatThrownBy(() -> adapter.synchronize(event())).isInstanceOf(RequestNotPermitted.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    private AccountChanged event() {
        return new AccountChanged(UUID.randomUUID(), "ACCOUNT_CREATED", Instant.now(), new AccountSnapshot(UUID.randomUUID(), "Test",
                "test@account.example", Plan.STARTER, AccountStatus.ACTIVE, "49.00", "USD", 0));
    }
}
