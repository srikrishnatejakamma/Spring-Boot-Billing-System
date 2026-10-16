package com.acme.worker.web;

import com.acme.billing.dto.DeliveryReport;
import com.acme.worker.persistence.InboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/worker")
public class WorkerOperations {
    private final InboxRepository inbox; private final String cohort;
    public WorkerOperations(InboxRepository inbox, @Value("${worker.cohort:main}") String cohort) { this.inbox = inbox; this.cohort = cohort; }
    @GetMapping("/events") @PreAuthorize("@accessPolicy.allows('ops.read')")
    public List<DeliveryReport> events() { return inbox.findTop100ByCohortOrderByReceivedAtDesc(cohort).stream().map(i -> i.report()).toList(); }
}
