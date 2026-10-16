# Use cases and consistency model

## UC1 — Idempotent billing-account onboarding

**Actor:** Billing operator or a calling service. **Trigger:** An organization subscribes to a plan.

The React form sends a generated `Idempotency-Key`. The backend validates and normalizes the request, hashes its canonical JSON, and commits an account, sync cursor, key receipt, and immutable full-state outbox event in one relational transaction. A repeated key with the same request returns the original account’s current state. A changed request with that key returns 409. Concurrent first uses of the same key serialize through a unique database constraint; the losing transaction rolls back completely and may retry the same key.

**Acceptance:** Repeating an onboarding request produces one account and one initial event. Invalid requests produce neither. No monetary fields use binary floating-point in Java or persistence.

## UC2 — Concurrent plan and account lifecycle changes

**Actor:** Billing operator. **Trigger:** Plan upgrade, suspension, reactivation, or account closure.

The form retains the version it opened. A command must match that version; JPA `@Version` also detects overlapping transactions after the initial comparison. The application flushes before serializing the event, ensuring event and account versions match. A lost race rolls back both state and event. A closed account cannot be edited or reopened. Suspended and closed accounts do not contribute to recurring revenue.

**Acceptance:** Two competing edits yield one committed update. A stale edit receives 409 and must reload before retrying. Plan pricing always comes from the backend’s authoritative plan catalogue.

## UC3 — Reliable synchronization across datastores

**Actor:** Outbox publisher and synchronization consumer. **Trigger:** A committed account change.

```mermaid
sequenceDiagram
  participant UI as React operator
  participant API as Account API
  participant SQL as MySQL ledger
  participant MQ as RabbitMQ
  participant Worker as Sync worker
  participant Platform as Billing platform
  participant Mongo as MongoDB projection
  UI->>API: Change account, expected version
  API->>SQL: Account + outbox event (one transaction)
  API-->>UI: New version, sync pending
  API->>MQ: Persistent event ID, mandatory routing
  MQ-->>API: Publisher confirmation
  MQ->>Worker: At-least-once event delivery
  Worker->>SQL: Lock event receipt + account sync cursor
  Worker->>Platform: Versioned snapshot, Idempotency-Key = event ID
  Worker->>Mongo: Apply only if snapshot version is newer
  Worker->>SQL: Persist cursor and DELIVERED receipt
  Worker-->>MQ: Listener returns; acknowledge delivery
```

The event payload is immutable and remains in SQL. RabbitMQ transports its ID. An event lock deduplicates concurrent processing; a per-account cursor lock serializes external updates. Events older than the cursor become completed no-ops. MongoDB uses a conditional version update so stale snapshots cannot overwrite newer state.

The platform and MongoDB do not participate in the SQL transaction. If an external update succeeds and a later step fails, the event retries with the same idempotency key. The external platform must support that key and version-aware writes; this is demonstrated by the included mock, not promised for every vendor.

**Acceptance:** An event delivered twice has one effect. Delivering v2 before v1 leaves destinations at v2. A crash after publication can cause a duplicate but does not lose the committed account change. Published events without a completion receipt are eligible for redrive after 30 seconds.

## UC4 — Diagnose an outage and recover delivery

**Actor:** Operations engineer. **Trigger:** An unavailable platform, broker, or document store.

Failed attempts remain visible with a sanitized error, next attempt time, and failure count. Backoff is 2, 4, 8, 16, 32, then 60 seconds; the sixth failure transitions to `DEAD_LETTER` and stops automatic dispatch. The operator restores the destination and selects **Retry now**, which starts a fresh six-attempt cycle while retaining the original event ID, account version, and payload. Successful completion clears the latest error.

The scheduled worker polls every two seconds in batches of 25. It is tuned for a demonstration, not measured production throughput. Broker poison messages use a separate `billing.poison` queue; application delivery dead letters are SQL events visible in the UI. Their recovery mechanisms are deliberately distinct.

**Acceptance:** An outage does not roll back a committed account. Restoring a destination synchronizes pending accounts. An already delivered event cannot be manually retried through the API.

## Requirement mapping

| Requirement area | Demonstration |
| --- | --- |
| Java, Spring Boot 3, Gradle modules | Core, integrations, API, frontend modules |
| GraphQL / Netflix DGS | Account, overview, and event queries; GraphiQL |
| Event-driven processing | Durable RabbitMQ exchange/queue, confirms, returns, receipts |
| MySQL/JPA and optimistic locking | Account entity, versioned commands, transaction-bound outbox |
| Non-relational synchronization | MongoDB projection with monotonic version updates |
| External REST integration | Timeout-bound HTTP adapter and idempotent contract simulator |
| Safe migrations | Flyway versioned schema; expand/contract deployment notes |
| Unit/integration validation | JUnit 5, Mockito, H2 workflow tests, Testcontainers infrastructure tests, React tests |
| GitLab quality gates | Coverage, frontend build/tests, mandatory infrastructure test stage |
| Datadog / canary practices | Micrometer counters/gauges and monitor-based promotion gate |
| Observability | Health/readiness endpoints, event IDs, structured log fields, delivery operations |

AWS SQS, Kafka, DynamoDB, Apollo clients, email delivery, and Zuora-specific SDK integration are alternative or additional technologies; this implementation selects RabbitMQ, MongoDB, DGS, and a generic HTTP billing-platform contract.

## Boundaries

- No distributed two-phase commit or exactly-once transport is claimed. The destination contract is what makes replay safe.
- The durable SQL event row doubles as the inbox receipt. Do not delete receipts while old messages can still be delivered. A standalone consumer service should own a separate inbox and consume complete event envelopes, with its own datastore and completion/reconciliation API.
- Account/event lists are bounded only for global event history (100). Add cursor pagination, retention, rate limits, lease-based publishing, and load testing before a large deployment.
- Network calls execute while holding event and per-account cursor locks, with bounded connection/read timeouts. This favors simple correctness; a scalable worker would claim leases and coordinate independent consumer state.
- Demo state is not the same as distributed state: H2 replaces MySQL, delivery is in-process, and the destination is a simulator. The distributed adapter is exercised by the separately selected infrastructure tests.
