package com.acme.billing.events;
public interface EventTransport { void send(String eventId) throws Exception; }
