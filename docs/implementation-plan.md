# Implementation analysis and plan

This plan records what the repository implements, what can be verified locally, and what still requires an owning deployment environment. Repository configuration is not evidence that a production control is active.

## Analysis of the current baseline

| Area | Implemented in this repository | Remaining gap |
| --- | --- | --- |
| Service boundaries | Java 21 multi-module build; independent billing service and event worker; shared domain/DTO and adapter modules; ArchUnit checks | Verify the real-infrastructure profile in CI and keep module-path references current |
| Financial integrity | Immutable ledger transactions and entries; balanced postings; exact `BigDecimal` amounts with two decimal places; idempotency and optimistic account versions | Ledger actor/description exist, but all account and ledger changes need a uniform append-only audit record with trace ID and justification |
| Event delivery | Transactional SQL outbox, confirmed RabbitMQ publication, independent worker inbox/leases, durable delivery reports, retries and dead letters | Safe operational DLQ inspection/redrive authorization and production queue retention are not fully specified |
| Resilience | Resilience4j circuit breaker and rate limiter; versioned destination cursors; migration compatibility test | No periodic external source-of-truth reconciliation job or alerting implementation |
| Security and privacy | JWT issuer/audience/signature/expiry validation, scopes, demo-only anonymous mode, config-tree secret support | Field-level encryption, managed KMS/Secrets Manager rotation, data retention/deletion policy and database grants need deployment-specific implementation |
| Observability | Request correlation ID, Micrometer metrics, Rabbit observation, OpenTelemetry exporter settings, Datadog quality gate | Verify end-to-end trace continuity in a live broker/APM environment; provision monitors and confirm ingestion |
| Operations | Health probes, configurable canary/gate/promotion/rollback commands, expand/contract migration guidance, graceful application and worker shutdown timeouts | Verify draining under live SIGTERM; measurable RPO/RTO and regional failover need environment evidence |
| API/resource controls | DGS field authorization, mapped batch loaders, input validation | Explicit GraphQL depth/complexity limits and externally governed REST/GraphQL contract tests are absent |
| Testing/CI | JUnit unit/workflow tests, migration compatibility test, Docker-backed infrastructure test task, React tests, Datadog gate tests | Containerized tests require a Docker-capable runner; no chaos suite or external contract provider verification |
| Optional AWS projection | Worker can write version-guarded account snapshots with AWS SDK for Java v2; CloudFormation provisions a pay-per-request, encrypted, point-in-time-recoverable table and a write-only policy | AWS credentials, deployed table, IAM attachment, DynamoDB Local/LocalStack integration run, and live AWS behavior require an AWS environment |

## Implementation plan

1. **Add mutation auditability.** Persist append-only audit rows in the same transaction as account and ledger mutations. Each row records trace ID, authenticated actor (or explicit system/demo actor), UTC timestamp, action, target, and a bounded justification. Add an authorized read path and tests for audit data and rollback behavior.
2. **Repair verification wiring.** Update GitLab jobs to use current Gradle modules/tasks. Ensure backend tests, coverage, frontend tests/build, gate tests, infrastructure tests, and packaging refer to the modules that exist today.
3. **Close operational behavior gaps.** Verify graceful shutdown and worker message draining under deployment signals; add tests for the canary rollback hook and rollback failure. Keep rollout commands environment supplied.
4. **Harden event operations.** Define authorized DLQ inspection/redrive with replay-safe generation/idempotency; add scheduled reconciliation with drift metrics/alerts and bounded, restartable batches.
5. **Add resource and compatibility guardrails.** Configure DGS depth/complexity limits and provider/consumer contract tests for the APIs that have concrete consumers.
6. **Document deployment controls and evidence.** Specify retention/deletion and encryption/rotation responsibilities, queue/database backup and restore checks, and RPO/RTO assumptions. Only claim KMS, live rotation, regional failover, Datadog ingestion, or canary traffic shifting when the target environment demonstrates them.
7. **Run the verification sequence.** Run fast module tests and coverage, frontend tests/build, CI gate tests, then Docker-backed infrastructure/system tests when Docker is available. Assemble the executable and record actual outcomes in this document.

## Acceptance criteria

- Every committed account or ledger mutation writes exactly one audit row in the same database transaction; failed mutations write none.
- Audit rows contain trace ID, actor, timestamp, action, target, and justification, and application code exposes no update/delete path for them.
- GitLab uses current project modules and gates packaging on the required test and analysis jobs.
- A failed/unavailable canary monitor prevents promotion and attempts rollback; rollback failure is visible as a failed job.
- Worker shutdown stops intake, completes or safely leaves leased work retryable, then closes resources.
- DLQ replay preserves idempotency and cannot apply an old account version over a newer projection.
- Reconciliation is bounded, restartable, observable, and does not mutate financial ledger history to hide drift.
- Any production claim for encryption, rotation, retention, regional recovery, or traffic shifting is backed by environment evidence.

## External validation boundaries

Protected branch approvals, production IAM/KMS grants, live secrets rotation, a deployed OIDC issuer, external vendor contracts, Datadog ingestion, registry publishing, Docker-capable CI runners, and the deployment platform require the owning environment. This repository can provide configuration, tests, and operator procedures, but local verification cannot prove those controls are active. The billing platform remains a contract simulator without a vendor API account and specification. The optional DynamoDB code and CloudFormation template do not prove hands-on production AWS experience.

## Verification log

- `gradlew.bat verify :billing-service:bootJar :event-worker:bootJar :config-server:bootJar --no-daemon --console=plain`: passed. This runs module unit/workflow tests, frontend tests/build, both coverage gates, and assembles all three executable jars.
- `gradlew.bat :system-tests:infrastructureTest --no-daemon --console=plain`: passed against MySQL 8.4, RabbitMQ, and MongoDB Testcontainers with independent billing and worker application contexts.
- `python -m unittest discover -s ci -p 'test_*.py'`: 2 tests passed.
- Optional DynamoDB account projection uses AWS SDK for Java v2 conditional `PutItem`; unit tests cover full-state mapping, monotonic version conditions, and safe duplicate/stale handling. `infra/dynamodb-projection.yml` provisions the table with encryption, point-in-time recovery, and a table-scoped write policy.
- `gradlew.bat verify :billing-service:bootJar :event-worker:bootJar :config-server:bootJar --no-daemon --console=plain`: passed after running outside the sandbox so Vitest/esbuild could spawn. Backend tests and coverage gates, frontend tests/build, and executable packaging all passed.
- `gradlew.bat :system-tests:infrastructureTest --no-daemon --console=plain`: passed outside the sandbox with Docker/Testcontainers.
- The optional DynamoDB adapter is unit-tested but was not run against DynamoDB Local or an AWS account. Attaching the generated IAM policy, provisioning the table, and validating AWS credentials/permissions remain deployment checks.
- Baseline CI review found retired module references and a canary gate without rollback invocation; `.gitlab-ci.yml` now uses current modules and invokes the supplied rollback command on gate failure.
- APM provider ingestion, production secrets/KMS, retention enforcement, cross-region recovery, real vendor contracts, and live traffic shifting remain environment-owned validation items.
