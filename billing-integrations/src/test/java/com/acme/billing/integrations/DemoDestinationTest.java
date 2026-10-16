package com.acme.billing.integrations;

import com.acme.billing.dto.*;

import com.acme.billing.core.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DemoDestinationTest {
    @Test void duplicateAndLateMessagesCannotRollBackTheProjection() {
        var destination = new DemoDestination();
        var id = UUID.randomUUID();
        destination.synchronize(event(id, 2, Plan.ENTERPRISE));
        destination.synchronize(event(id, 1, Plan.STARTER));
        destination.synchronize(event(id, 2, Plan.ENTERPRISE));
        assertThat(destination.snapshots()).hasSize(1);
        assertThat(destination.snapshots().get(id).plan()).isEqualTo(Plan.ENTERPRISE);
        assertThat(destination.snapshots().get(id).version()).isEqualTo(2);
    }
    @Test void outageDoesNotApplyAnAccount() {
        var destination = new DemoDestination();
        destination.setAvailable(false);
        assertThatThrownBy(() -> destination.synchronize(event(UUID.randomUUID(), 0, Plan.STARTER)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(destination.snapshots()).isEmpty();
    }
    private AccountChanged event(UUID id, long version, Plan plan) {
        return new AccountChanged(UUID.randomUUID(), "ACCOUNT_UPDATED", Instant.now(),
                new AccountSnapshot(id, "Acme", "billing@acme.example", plan, AccountStatus.ACTIVE,
                        plan.monthlyRate().toPlainString(), "USD", version));
    }
}
