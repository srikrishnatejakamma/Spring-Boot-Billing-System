package com.acme.billing;

import com.acme.billing.account.SyncRepository;
import com.acme.billing.events.*;
import com.acme.billing.integrations.AccountDestination;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;

class DeliveryProcessorTest {
    @Test void alreadyDeliveredMessagesNeverCallTheExternalDestination() {
        var events = mock(OutboxRepository.class); var syncs = mock(SyncRepository.class);
        var destination = mock(AccountDestination.class); var event = new OutboxEvent();
        event.status = EventStatus.DELIVERED;
        when(events.lock("event-01")).thenReturn(Optional.of(event));
        new DeliveryProcessor(events, syncs, destination, new ObjectMapper(), new SimpleMeterRegistry()).receive("event-01");
        verifyNoInteractions(destination, syncs);
    }
}
