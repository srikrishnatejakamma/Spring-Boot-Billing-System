package com.acme.worker.service;

import com.acme.worker.domain.InboxStatus;
import com.acme.worker.messaging.ReportTransport;
import com.acme.worker.persistence.InboxRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component @ConditionalOnProperty(name = "worker.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkerScheduler.class);
    private final InboxRepository repository;
    private final InboxService inbox;
    private final DeliveryJob jobs;
    private final ReportTransport reports;
    private final String cohort;
    public WorkerScheduler(InboxRepository repository, InboxService inbox, DeliveryJob jobs, ReportTransport reports,
                           @Value("${worker.cohort:main}") String cohort) {
        this.repository = repository; this.inbox = inbox; this.jobs = jobs; this.reports = reports; this.cohort = cohort;
    }
    @Scheduled(fixedDelayString = "${worker.poll-delay:1000}")
    public void process() {
        for (String id : repository.due(cohort, List.of(InboxStatus.PENDING, InboxStatus.RETRYING), Instant.now(), PageRequest.of(0, 25))) {
            try { jobs.run(id); }
            catch (Exception exception) { log.warn("worker_claim_deferred eventId={} cause={}", id, exception.getClass().getSimpleName()); }
        }
    }
    @Scheduled(fixedDelayString = "${worker.report-delay:500}")
    public void publishReports() {
        for (String id : repository.unreported(cohort, List.of(InboxStatus.RETRYING, InboxStatus.DEAD_LETTER, InboxStatus.DELIVERED), PageRequest.of(0, 50))) {
            try {
                var report = inbox.report(id); reports.publish(report); inbox.reported(id, report.revision());
            } catch (Exception exception) { log.warn("worker_report_deferred eventId={} cause={}", id, exception.getClass().getSimpleName()); }
        }
    }
}
