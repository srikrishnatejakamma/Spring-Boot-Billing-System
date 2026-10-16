# Billing Control Center

A runnable billing-account and ledger system that demonstrates correctness-focused backend engineering with Java 21 and Spring Boot 3. It includes an operator console, REST and GraphQL APIs, an immutable double-entry ledger, and an independently executable event worker for synchronizing account changes with external systems.

## Features

- **Billing account lifecycle:** Create accounts, update plans, suspend or close accounts, and inspect recurring revenue. Prices are authoritative backend values and use `BigDecimal` decimal arithmetic.
- **Idempotent, concurrency-safe commands:** Account creation and ledger posting accept idempotency keys. Optimistic versions reject stale account updates with HTTP 409.
- **Double-entry ledger:** Post balanced debit/credit transactions, retain immutable transactions and entries, and review account history. Ledger support is enabled in the demo profile; it can be controlled by configuration in other profiles.
- **Transactional event delivery:** Account state, sync cursor, and immutable outbox event commit together. RabbitMQ publication uses durable messages, publisher confirms, and mandatory routing.
- **Independent synchronization worker:** The worker owns its inbox, leases, and delivery reports. It consumes event IDs, deduplicates replays, preserves per-account version order, and updates an external billing-platform endpoint and a MongoDB projection.
- **Failure recovery and resilience:** Bounded retries with backoff, dead-letter handling, operator retry, destination circuit breaking, rate limiting, and outage simulation.
- **Operator views and APIs:** React dashboard, account forms, event stream, audit history, recovery controls, GraphQL queries, and GraphiQL explorer.
- **Security and audit:** Distributed services validate JWT issuer, audience, signature, and expiry, then enforce scopes for protected operations. Mutations write append-only audit records with actor and request correlation details. The local demo profile intentionally disables authentication.
- **Centralized configuration and operations:** Spring Cloud Config Server, environment/config-tree secret inputs, health/readiness probes, structured logs, Micrometer metrics, OpenTelemetry tracing settings, and optional Datadog metrics and canary quality gate.
- **Schema and quality controls:** Flyway migrations, Hibernate schema validation, migration compatibility coverage, JaCoCo coverage gates, architecture tests, unit/workflow tests, and Docker-backed system tests.

## Technology stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring MVC, Spring Data JPA, Hibernate |
| API | REST, GraphQL with Netflix DGS, GraphiQL, Bean Validation |
| Service configuration and security | Spring Cloud Config, Spring Security OAuth 2.0 Resource Server, JWT/JWK |
| Messaging and resilience | RabbitMQ, Spring AMQP, Resilience4j circuit breaker and rate limiter |
| Persistence | MySQL 8.4, MongoDB 7, optional DynamoDB projection (AWS SDK v2), H2 for the local demo, Flyway |
| Frontend | React 19, Vite 6, Lucide React |
| Observability | Spring Boot Actuator, Micrometer, OpenTelemetry OTLP tracing, Datadog metrics |
| Build and validation | Gradle multi-module build, JUnit 5, Mockito, AssertJ, Awaitility, Testcontainers, ArchUnit, Vitest, GitLab CI |
| Local infrastructure | Docker Compose, Node.js 22 billing-platform simulator |

The external platform is a generic HTTP contract simulator, not a Zuora integration. The project does not charge payment cards or generate invoices. RabbitMQ and DGS cover the message-broker and GraphQL alternatives in the role requirements; this project does not add SQS, Kafka, or an Apollo client. DynamoDB is an optional AWS SDK v2 account projection and is disabled by default.

## Run the local demo

Prerequisites: **JDK 21** and **Node.js 22** with npm. The Gradle wrapper is included. The first build downloads Java dependencies.

From the repository root in PowerShell:

```powershell
# Terminal 1 — Spring Boot API at http://localhost:8080
.\gradlew.bat :billing-service:bootRun

# Terminal 2 — React console at http://localhost:5173
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. The demo profile seeds sample accounts and stores its H2 ledger under `data/`. Delivery runs locally in the billing service and uses an in-memory destination simulation. The demo profile is for local evaluation and does not require MySQL, RabbitMQ, or MongoDB.

To package the frontend into the executable service jar:

```powershell
.\gradlew.bat :billing-service:bootJar
java -jar billing-service/build/libs/billing-service-1.0.0.jar
```

Open **http://localhost:8080**. Stop any other service using that port first.

On macOS or Linux use `./gradlew` instead of `gradlew.bat` (run `chmod +x gradlew` once if needed).

## Run the distributed profile

Requires Docker and a running Docker daemon. Compose starts MySQL 8.4, RabbitMQ with management UI, MongoDB 7, and the sample HTTP billing platform. The service processes are started separately so the billing API and worker remain independent.

```powershell
# Start infrastructure
docker compose up -d --wait

# Terminal 1 — configuration server at http://localhost:8888
.\gradlew.bat :config-server:bootRun

# Terminal 2 — billing API at http://localhost:8080
.\gradlew.bat :billing-service:bootRun --args="--spring.profiles.active=distributed"

# Terminal 3 — event worker at http://localhost:8082
.\gradlew.bat :event-worker:bootRun --args="--spring.config.name=worker --spring.profiles.active=distributed"

# Terminal 4 — React console at http://localhost:5173
cd frontend
npm ci
npm run dev
```

The local Config Server credentials default to `config-local` / `config-local-password`; the sample repository configuration uses local development credentials for the services and broker. Replace them through environment variables or mounted secrets outside local development. The distributed profile starts with an empty ledger. The simulator is at **http://localhost:8090/accounts** and RabbitMQ management is at **http://localhost:15672** (`billing` / `billing-local`).

The worker can also write a version-guarded account projection to DynamoDB. Provision the table and least-privilege write policy from `infra/dynamodb-projection.yml`, attach that policy to the worker's AWS role, and set `WORKER_DYNAMODB_ENABLED=true` and `WORKER_DYNAMODB_TABLE` in the worker environment. The AWS SDK v2 default credential chain is used; `WORKER_DYNAMODB_ENDPOINT` is available for a local DynamoDB endpoint. This destination is disabled unless explicitly enabled.

To simulate an HTTP platform outage, then restore it:

```powershell
Invoke-RestMethod http://localhost:8090/admin/outage -Method Put -ContentType application/json -Body '{"enabled":true}'
# Make an account change and inspect the retry state in the console.
Invoke-RestMethod http://localhost:8090/admin/outage -Method Put -ContentType application/json -Body '{"enabled":false}'
```

An event that exhausts its retry policy is marked dead letter and can be retried from the operator console. The simulator enforces an idempotent, version-aware `PUT /accounts/{id}` contract. Stop the Compose infrastructure with `docker compose down`; named data volumes are preserved.

## Verify

```powershell
.\gradlew.bat verify
.\gradlew.bat :system-tests:infrastructureTest
python -m unittest discover -s ci -p 'test_*.py'
```

`verify` runs the Gradle module tests, targeted Java coverage checks, and frontend tests/build. The system test task uses Testcontainers to exercise real MySQL, RabbitMQ, and MongoDB and requires Docker. The GitLab pipeline also runs these checks before packaging, with deployment and promotion stages enabled only when the owning environment supplies its canary, rollback, monitor, and promotion settings.

## Project layout

| Module / path | Responsibility |
| --- | --- |
| `common-domain` | Framework-independent domain values, validation, and monetary rules |
| `common-dto` | Versioned API, event, and delivery-report contracts |
| `billing-integrations` | Billing-platform HTTP adapter, MongoDB and optional DynamoDB projections, and resilience decorators |
| `platform-support` | Shared JWT security, authorization, audit context, and observability configuration |
| `billing-service` | Account and ledger REST APIs, DGS GraphQL API, JPA persistence, Flyway migrations, transactional outbox, and demo delivery |
| `event-worker` | Independent RabbitMQ consumer, SQL inbox/leases, MongoDB projection, external destination delivery, and reports |
| `config-server` | Spring Cloud Config Server with a native local configuration repository |
| `system-tests` | Cross-service infrastructure tests using Testcontainers |
| `frontend` | React/Vite operator dashboard and recovery console |
| `infra` | Idempotent HTTP billing-platform simulator |
| `ci` | Datadog monitor quality gate and its tests |
| `.gitlab-ci.yml` | Verification, infrastructure, packaging, and optional canary/gate/promotion stages |

See [use cases and consistency model](docs/use-cases.md), [operations and deployment notes](docs/operations.md), and [architecture decisions](docs/architecture-decisions.md) for workflow details and system boundaries.

## API quick reference

| Endpoint | Purpose |
| --- | --- |
| `GET /api/accounts?limit=100` | List accounts and their synchronization state |
| `GET /api/accounts/{id}` | Read an account |
| `POST /api/accounts` | Create an account; requires `Idempotency-Key` |
| `PUT /api/accounts/{id}` | Update an account using its current `version` |
| `GET /api/accounts/{id}/transactions?limit=50` | Read ledger history when ledger support is enabled |
| `POST /api/accounts/{id}/transactions` | Post a balanced ledger transaction |
| `GET /api/audit` | Read recent append-only audit entries; requires `billing.audit` |
| `GET /api/overview` | Account counts, recurring revenue, and delivery counts |
| `GET /api/events?accountId=…` | Read recent events, optionally filtered by account |
| `POST /api/events/{id}/retry` | Retry an eligible failed event |
| `GET /api/worker/events` | Read worker delivery reports (authorized in secured mode) |
| `POST /graphql`, `GET /graphiql` | DGS query API and interactive explorer |
| `GET /actuator/health`, `/actuator/metrics` | Health, readiness, delivery metrics, and outbox gauges |

Account creation body: `{"company":"Acme","billingEmail":"billing@acme.example","plan":"GROWTH"}`. Plans are `STARTER` ($49), `GROWTH` ($149), and `ENTERPRISE` ($499) per month. Account status is `ACTIVE`, `SUSPENDED`, or `CLOSED`. These are subscription list prices; the application does not charge cards or issue invoices.

## Configuration and deployment notes

Distributed mode uses the Config Server at `http://localhost:8888` by default. Service connection strings, JWT issuer/audience settings, tracing, and Datadog export can be supplied through environment variables and config-tree secrets. The sample app binds to loopback by default; container deployments can override `SERVER_ADDRESS`.

Read [deployment and migration notes](docs/operations.md) for Datadog monitor configuration, GitLab variables, schema migration practices, and security boundaries. Canary deployment commands and monitor IDs are environment-owned and are not active by default.

This repository is an engineering demonstration. Before exposing real account data, configure a trusted OIDC provider, production-grade authorization and tenant isolation, managed secrets, database access controls, retention rules, and deployment-specific operational safeguards. The project does not claim compliance certification or production readiness.

## Enterprise controls: implemented and remaining

The following matrix describes the scope demonstrated by this repository. Configuration and guidance are not evidence that a production control has been deployed or independently validated.

| Area | Implemented in this project | Not implemented or environment-owned |
| --- | --- | --- |
| Financial audit and compliance | Immutable ledger transactions and entries; account and ledger mutations record actor, trace/correlation ID, UTC timestamp, action, target, and justification. Audit reads are scope-protected. | This is not a SOC 1/SOC 2 control certification. Production database grants, tamper evidence, independent audit review, approved retention, and compliance evidence must be supplied by the operating environment. |
| Data protection and retention | JWT validation and operation scopes are configurable; secrets can be provided through environment variables or Spring config-tree mounts. | KMS-backed encryption-at-rest, field-level PII/payment-data encryption or masking, automatic key/credential rotation, and GDPR/CCPA deletion-versus-ledger-retention policies are not implemented. Do not place live secrets in the sample configuration. |
| Event consistency and concurrency | The billing service commits account state and an outbox event in one SQL transaction. A separate RabbitMQ worker processes event IDs with an inbox, leases, idempotency, version checks, and a MongoDB projection; an optional AWS SDK v2 adapter also writes a version-guarded DynamoDB projection. Account updates use optimistic version checks. | This implementation does not use CDC, Kafka, or SQS because RabbitMQ covers the broker requirement. It has no distributed lock for multi-account billing-cycle operations; cross-aggregate locking would need a specific design if such workflows are added. |
| Reconciliation | Delivery state, sync cursors, and destination outcomes are observable; failed events can be retried. | A periodic source-of-truth reconciliation engine that detects external drift and raises alerts is not included. |
| Deployment and migrations | GitLab CI provides verification, packaging, an optional manual canary stage, a Datadog monitor gate, rollback command hook on gate failure, and manual promotion. Flyway migrations and expand/contract guidance are included. | Traffic percentages, ingress/service-mesh routing, deployment commands, monitor IDs, and production rollback behavior are supplied by the deployment environment. Expand/contract is guidance, not an automatically enforced zero-downtime guarantee. |
| Integration and resilience testing | JUnit tests and a Testcontainers system test exercise MySQL, RabbitMQ, MongoDB, duplicate messages, and a simulated downstream outage. DynamoDB conditional-write behavior has SDK request unit tests. | Pact or other consumer-driven contract tests, a live DynamoDB Local/LocalStack test, Kafka/SQS test infrastructure, and a broad chaos/fault-injection suite are not included. |
| Rate limits and GraphQL safeguards | Resilience4j circuit breaking and rate limiting protect the external destination path; GraphQL reads use Netflix DGS. | Vendor-specific quota/backpressure policies and GraphQL depth or complexity limits are not implemented. |
| Monetary precision | Monetary values use `BigDecimal`; ledger amounts are stored as `DECIMAL(12,2)`. Input validation rejects values that cannot be represented exactly to two decimal places (`RoundingMode.UNNECESSARY`). | Currency conversion, multi-currency rounding policy, and a broader financial rounding governance framework are outside this demo. |
| Tracing and shutdown | Micrometer/OpenTelemetry tracing settings, request correlation IDs, graceful application shutdown, worker listener shutdown timeouts, and scheduler termination waits are configured. | End-to-end W3C trace-context continuity across RabbitMQ and downstream calls has not been validated as a production tracing guarantee. Orchestrator-level draining and deployment behavior need environment testing. |
| Disaster recovery and DLQ operations | SQL delivery events expose retry status and support operator retry; the worker also defines a RabbitMQ poison-message queue. | RPO/RTO targets, regional failover, split-brain behavior, database/broker restore procedures, and a dedicated inspect/sanitize/re-drive workflow for arbitrary broker DLQ messages are not defined here. |

For detailed consistency guarantees, tradeoffs, and operational gaps, see [use cases](docs/use-cases.md), [operations](docs/operations.md), and the [implementation plan](docs/implementation-plan.md).
