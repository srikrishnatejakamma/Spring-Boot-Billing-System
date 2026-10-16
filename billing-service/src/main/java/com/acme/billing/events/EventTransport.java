package com.acme.billing.events;
import com.acme.billing.dto.EventEnvelope;
public interface EventTransport { void send(EventEnvelope event, String routingKey) throws Exception; }
