package com.acme.worker.messaging;
import com.acme.billing.dto.DeliveryReport;
public interface ReportTransport { void publish(DeliveryReport report) throws Exception; }
