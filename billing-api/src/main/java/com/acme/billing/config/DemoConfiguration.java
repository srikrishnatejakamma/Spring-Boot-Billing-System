package com.acme.billing.config;

import com.acme.billing.events.EventTransport;
import com.acme.billing.integrations.DemoDestination;
import org.springframework.context.annotation.*;

@Configuration @Profile("!distributed")
public class DemoConfiguration {
    @Bean public DemoDestination demoDestination() { return new DemoDestination(); }
    @Bean public EventTransport localTransport() { return eventId -> {}; }
}
