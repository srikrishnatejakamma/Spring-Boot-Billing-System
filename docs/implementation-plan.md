# Four-phase implementation plan

Status labels distinguish implemented code, passing local verification, and external deployment validation. An example config or prose policy is not evidence that an external control is active.

## Analysis of the existing baseline

| Area | Existing implementation | Gap |
| --- | --- | --- |
| Modules | `billing-core`, `billing-integrations`, `billing-api`, React | Worker is embedded; shared DTO/domain boundaries are not separate |
| Runtime/configuration | Java 21, Spring Boot 3, one BOM, environment overrides | No centralized config service or token validation |
| Governance | Review guidance in operations notes | No dedicated AI policy, review checklist, architecture/scanning gates |
| Persistence | Account, sync cursor, idempotency receipt, outbox; JPA versions | No normalized financial ledger and immutable transaction history |
| Migrations | Flyway initial schema and expand/contract documentation | No tested upgrade/backward-compatibility scenario or staging dry-run job |
| GraphQL | Schema-first DGS account/overview/event queries | No nested batch loading, field authorization, or explicit error codes |
| Delivery | Confirmed RabbitMQ publication, retry/backoff, deduplication, dead letters | Shared SQL receipts; no independent worker inbox/leases or circuit/rate controls |
| Integration | HTTP adapter plus MongoDB versioned projection | Distributed configuration fails to create MongoTemplate; vendor remains a contract simulator |
| Quality | 14 Java tests, 4 React tests, targeted 80% coverage, gate unit tests | Distributed test failing; no static/security scans or container publishing |
| Operations | Metrics, health, Datadog gate script, configurable deploy commands | No APM configuration, JSON logging, provisionable monitors, or automatic rollback |

The distributed baseline failure is an auto-configuration issue: exclusions in the base application configuration remain effective when the distributed profile is selected. The first regression check must prove MongoDB and RabbitMQ start correctly under that profile.

## Implementation sequence

1. **Stabilize the baseline.** Correct profile-specific auto-configuration and pass the existing real MySQL/RabbitMQ/MongoDB workflow test.
2. **Establish service boundaries.** Introduce `common-domain`, `common-dto`, `billing-service`, and an independently executable `event-worker`; retain integration adapters and React. Add architectural dependency checks. Keep a clearly identified standalone demo for local exploration.
3. **Standardize configuration and security.** Centralize dependency versions; add Spring Cloud Config with external configuration/secrets; validate signed JWT issuer, audience, expiry and scopes. Supply a local OIDC development setup and a React sign-in path. Anonymous access is confined to explicit demo mode.
4. **Complete domain consistency.** Add normalized, balanced ledger postings and read-only transaction history with exact decimal amounts, idempotency, and optimistic concurrency. Add additive Flyway migrations and verify upgrade compatibility.
5. **Strengthen GraphQL.** Add nested transaction-history queries through a request-scoped batch loader, field-level permission checks, and predictable error extensions. Verify batching and authorization behavior.
6. **Separate event processing.** Publish complete versioned envelopes. Give the worker its own inbox and database, bounded processing leases, monotonic destination updates, retry/dead-letter operations, circuit breaking and rate limiting. Report delivery results through durable broker messages instead of sharing application entities.
7. **Complete operational controls.** Add static checks, dependency/container scanning, image publishing, migration dry-runs, JSON logs, APM export configuration, monitor definitions, and executable canary rollback/promotion tooling with failure tests.
8. **Verify the delivered system.** Run fast checks first, then sequential real-infrastructure tests, assemble all applications, and smoke-test the packaged React/backend workflow. Record commands and outcomes.

## Acceptance criteria

- Billing and worker applications build and start independently and do not depend on each other's persistence entities or repositories.
- Replayed onboarding and posting commands do not duplicate accounts, ledger postings, or downstream effects.
- Concurrent updates reject stale versions; every ledger transaction balances exactly; old events cannot move a destination backward.
- Unauthenticated, expired, wrong-issuer, wrong-audience and insufficient-scope requests are rejected in secured mode. Restricted GraphQL fields return a structured authorization error.
- Nested account histories use a bounded number of database queries as account count grows.
- Destination outages leave durable work for retry, open the circuit when appropriate, and move exhausted work to dead letter. Remote I/O runs outside long-lived relational transactions in the separate worker.
- Migrations upgrade an existing database without changing an already applied migration; the previous schema's essential reads remain valid during additive deployment.
- Static analysis, coverage, frontend tests, Java tests, security scans and infrastructure tests are explicit CI gates. Registry publishing and migration rehearsal have runnable jobs.
- A failed Datadog check prevents promotion and invokes rollback. Tests cover failure, no data, API error and rollback failure.
- A runnable artifact containing the React frontend passes an HTTP smoke test.

## External validation boundaries

GitLab protected-branch approvals, real code owners, registry access, a deployed OIDC issuer, Datadog monitor/APM ingestion, and a staging/production orchestrator require the owning environment. This repository will contain executable configuration and tests for those integrations. Their live activation will be reported separately and will not be claimed from local tests alone. The billing vendor remains a documented REST contract simulator unless a vendor account and API contract are provided.

## Verification log

- Baseline: account API tests 11/11 passing; core/adapter tests 3/3 passing; React tests 4/4 passing; targeted coverage gate passing.
- Baseline distributed test: failing application startup due to missing MongoTemplate; correction pending.
- Expanded phase checks: pending implementation.
