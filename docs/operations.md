# Operating and extending the demonstration

## Profiles and recovery

The default profile uses file-backed H2 and a local delivery worker. The `distributed` profile enables MySQL, RabbitMQ and MongoDB auto-configuration, and replaces the simulator with an HTTP + MongoDB destination. Compose services bind published ports to loopback.

The ledger is the source of truth. A sync cursor records the highest version that completed both destination writes; an account is shown as synced only when that cursor catches up. `PUBLISHED` means broker-confirmed publication, not completed processing. A 30-second lease permits publication to be repeated if consumer completion never arrives.

Demo projection data is in memory. On restart, completed events are replayed in ascending aggregate-version order into that simulation. This does not resend external HTTP requests. The mock HTTP platform in Compose is also intentionally ephemeral: after replacing it, rebuild it using the retained ledger snapshots or a dedicated reconciliation process. Production integrations need a durable platform and periodic reconciliation for out-of-band drift.

## Metrics and Datadog

Actuator exposes `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and `/actuator/metrics`. Useful Micrometer names:

| Meter | Meaning |
| --- | --- |
| `billing.delivery.success` | Successfully completed account synchronization |
| `billing.delivery.failures` | Failed destination or publication attempts |
| `billing.delivery.duplicates` | Already completed or superseded event deliveries |
| `billing.outbox.pending` | Pending, published, or retrying events |
| `billing.outbox.dead` | Events requiring operator replay |

Logs correlate operations with `eventId`, `accountId`, version or attempt; billing emails and full vendor error bodies are not written to application failure logs or error receipts. Deploy an APM agent independently if distributed traces are required.

Enable export with `DD_METRICS_ENABLED=true`, `DD_API_KEY`, `DD_APP_KEY`, `DD_ENV`, and the site-specific HTTPS `DD_API_URI`. Configure credentials as masked/protected CI or runtime secrets. The exporter uses a 30-second reporting interval. API keys do not belong in frontend configuration.

Create monitors scoped to the canary environment/service, with no-data notification enabled. Example query templates (verify the exact exported meter names in your Datadog organization):

```text
max(last_5m):max:billing.outbox.dead{service:billing-control-center,env:canary} > 0
sum(last_5m):sum:billing.delivery.failures{service:billing-control-center,env:canary}.as_count() > 5
max(last_5m):max:billing.outbox.pending{service:billing-control-center,env:canary} > 100
```

Add an HTTP error/latency monitor from your ingress or APM and an uptime monitor. The monitor gate requires every configured monitor’s aggregate status to be exactly `OK`; warnings, alerts, no data, missing statuses, API failures, and invalid configuration fail closed.

## GitLab CI and canary rollout

The default pipeline executes backend tests, the targeted coverage gate, React tests/build, real-infrastructure Testcontainers tests, and gate unit tests before packaging the executable jar. The infrastructure runner must support Docker-in-Docker (privileged service runner) or equivalent Docker access. Do not enable this runner for untrusted pipelines.

The deployment stages are templates for the owning environment. Set these protected variables to enable them on the default branch:

- `CANARY_DEPLOY_COMMAND`: a real command/script available inside the job image that deploys the packaged artifact to a small canary population and waits for readiness.
- `PROMOTE_COMMAND`: a real command/script that promotes the same artifact, with a rollback procedure owned by the deployment platform.
- `DD_MONITOR_IDS`: comma-separated numeric IDs for the canary-scoped monitors.
- `DD_API_KEY`, `DD_APP_KEY`, optional `DD_API_URI`: monitor-read credentials and Datadog site.

Replace the Alpine deployment job image with the environment’s deployment tooling when needed. The supplied commands run only in GitLab jobs after manual approval; no deployment is performed by building this repository. Configure the canary runtime with `DD_ENV=canary`, representative traffic, and matching monitor tags.

After the canary command succeeds, the monitor job waits 120 seconds for metrics, then checks every 30 seconds for six samples. Set `DD_WARMUP_SECONDS`, `DD_GATE_INTERVAL_SECONDS`, and `DD_GATE_CHECKS` to match the actual monitor evaluation window. Passing gates permit a separate manual promotion. No-data monitors and traffic checks are essential to distinguish a healthy canary from an idle one.

## Schema and entity migrations

`V1__account_and_outbox.sql` creates the ledger, idempotency receipts, sync cursor and indexed outbox. Hibernate validates the schema; it never changes it. Flyway applies migrations against the selected datasource. Never edit an applied migration.

For an additive entity change:

1. Add a nullable column in `V2__…sql`, tested against a copy of the production MySQL version and representative data.
2. Deploy readers that tolerate both old and new data and event payloads. Preserve old JSON fields while consumers are mixed-version.
3. Backfill in bounded, restartable batches with a cursor and reconciliation counts; do not hold a table-wide transaction.
4. Switch writers, validate completeness and data quality, and monitor canary delivery/error rates.
5. Add constraints or remove legacy columns only in a subsequent deployment after all old readers/writers and rollback versions have retired.

For irreversible changes, use reviewed backups, a restore rehearsal, an explicit rollback or roll-forward plan, and database-specific lock/DDL timing measurements. This repository includes the initial schema and a migration workflow; it does not fabricate a completed production migration.

## Review and security boundaries

This is a local example with no end-user authentication. Before handling real financial-account data, implement authenticated operator and service identities, tenant-scoped authorization, an append-only audit history including operator identity, encrypted transport and storage, managed credentials, retention and deletion rules, backup verification, and pagination/operational capacity limits. The current event history records account changes but is not a compliance audit log.

Independently review generated changes, dependency updates, financial calculations, transaction boundaries, replay behavior and migration plans. Run `verify` and the real-infrastructure suite; inspect coverage and failure logs. AI assistance does not replace engineering review or production validation.
