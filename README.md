# Ledger — Billing Control Center

A runnable billing-account use case built from [requirement.txt](requirement.txt): Java 21, **Spring Boot 3**, a **Gradle multi-module build**, and a **React** operations console.

Create billing accounts, change plans or account status, inspect versioned events, simulate a billing-platform outage, and recover failed deliveries. All figures in the UI come from the backend; subscription prices use exact decimal arithmetic.

## Run the standalone demo

Prerequisites: **JDK 21** and **Node.js 22** with npm. The Gradle wrapper is included; no globally installed Gradle is needed. Initial builds download dependencies.

From this directory in PowerShell:

```powershell
# Terminal 1 — Spring Boot, http://localhost:8080
.\gradlew.bat :billing-api:bootRun

# Terminal 2 — React, http://localhost:5173
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Eight sample accounts are seeded on the first run. The ledger persists in `data/`; the explicitly labeled demo destination is an in-memory simulation and rebuilds from retained events when the application restarts.

On macOS/Linux use `./gradlew` (run `chmod +x gradlew` once if needed). All other commands are the same.

To build a single executable containing the React frontend:

```powershell
.\gradlew.bat :billing-api:bootJar
java -jar billing-api/build/libs/billing-api-1.0.0.jar
```

Then open **http://localhost:8080**. Stop the development backend before running the jar on the same port.

## Try these workflows

1. **Onboard an account.** Select **New account**, enter a company, billing email, and plan. Its account record and synchronization event commit together. Watch the sync state become **Synced**.
2. **Change a plan safely.** Open an account and select a new plan. The monthly rate updates and a new version is emitted. An update with an outdated version receives HTTP 409. Closed accounts remain read-only.
3. **Recover from an outage.** Open **Reliability → Simulate platform outage**, then create or edit an account. Inspect the event’s retries in **Event stream**, restore the platform, and watch automatic recovery. Six consecutive failures move an event to dead letter; select **Retry now** after restoring the destination.
4. **Query the account graph.** Open **GraphQL explorer** and run the query below. REST handles commands; Netflix DGS handles GraphQL reads.

```graphql
query BillingWorkspace {
  overview { activeAccounts monthlyRevenue pendingEvents failedEvents }
  accounts { id company plan version syncStatus syncedVersion }
  events { id accountId accountVersion status attempts lastError }
}
```

## Run the distributed profile

Requires a running Docker daemon. Docker Compose provides MySQL 8.4, RabbitMQ with management UI, MongoDB, and an HTTP billing-platform simulator. This profile starts with an empty ledger.

```powershell
docker compose up -d --wait
.\gradlew.bat :billing-api:bootRun --args="--spring.profiles.active=distributed"
# Run the React dev server in a second terminal as above.
```

Or build and run the entire application in Docker:

```powershell
docker compose --profile app up --build -d --wait
```

Open http://localhost:8080 for the containerized app. RabbitMQ management is at http://localhost:15672 (`billing` / `billing-local`). Inspect the mock platform at http://localhost:8090/accounts. Stop the services with `docker compose --profile app down`; named datastore volumes are preserved.

The mock implements an idempotent, version-aware `PUT /accounts/{id}` contract. It is an example integration, **not a Zuora client**. To simulate a real HTTP integration failure:

```powershell
Invoke-RestMethod http://localhost:8090/admin/outage -Method Put -ContentType application/json -Body '{"enabled":true}'
# Change an account, then restore the destination:
Invoke-RestMethod http://localhost:8090/admin/outage -Method Put -ContentType application/json -Body '{"enabled":false}'
```

## Verify

```powershell
.\gradlew.bat verify                         # Java tests, coverage gate, React tests/build
.\gradlew.bat :billing-api:infrastructureTest # Actual MySQL + RabbitMQ + MongoDB via Testcontainers
python -m unittest discover -s ci -p 'test_*.py'
```

The infrastructure task requires Docker and is deliberately separate from the fast H2 tests. It is mandatory in the included GitLab pipeline. Coverage enforces 80% line coverage on account command processing and outbox failure-state behavior; reports are under `billing-api/build/reports/jacoco/test/html/index.html`.

The build defaults to one Gradle worker to keep Java and React verification reliable on development machines with limited free memory. On a larger machine, override this with `--max-workers=4`.

## Project layout

| Module / path | Responsibility |
| --- | --- |
| `billing-core` | Framework-independent event contracts, enums, exact plan prices |
| `billing-integrations` | Demo destination and external REST + MongoDB adapters |
| `billing-api` | Spring Boot, REST, Netflix DGS, JPA, Flyway, transactional outbox, worker |
| `frontend` | React + Vite, dashboard, forms, event operations, recovery lab |
| `infra` | Idempotent billing-platform HTTP simulator |
| `ci` | Datadog monitor quality gate and its tests |
| `.gitlab-ci.yml` | Tests, coverage, infrastructure tests, packaging, optional canary/promotion gates |

This is a **modular application with an event-driven worker**, not a fleet of separately deployed microservices. In distributed mode, the publisher and consumer intentionally share the ledger and its durable event receipts. See [use cases and architecture](docs/use-cases.md) for consistency guarantees, tradeoffs, and the path to independent services.

## API quick reference

| Endpoint | Purpose |
| --- | --- |
| `GET /api/accounts` | List current ledger account state and sync cursor |
| `GET /api/accounts/{id}` | Read one account |
| `POST /api/accounts` | Create; requires `Idempotency-Key` header |
| `PUT /api/accounts/{id}` | Update; requires current `version` in request body |
| `GET /api/overview` | Account counts, recurring revenue, delivery counts |
| `GET /api/events?accountId=…` | Latest 100 events, or all events for one account |
| `POST /api/events/{id}/retry` | Reset failed event for immediate dispatch |
| `PUT /api/demo/platform` | Demo-only outage switch: `{"available":false}` |
| `GET /api/demo/projection` | Demo destination state |
| `POST /graphql`, `GET /graphiql` | DGS query API and explorer |
| `GET /actuator/health`, `/actuator/metrics` | Health, delivery counters, outbox gauges |

Account creation body: `{"company":"Acme","billingEmail":"billing@acme.example","plan":"GROWTH"}`. Valid plans: `STARTER` ($49), `GROWTH` ($149), `ENTERPRISE` ($499). Prices are monthly USD subscription list prices; the app does not charge cards or generate invoices. Update adds `"status":"ACTIVE","version":0`; status may also be `SUSPENDED` or `CLOSED`.

## Deployment and observability

Read [deployment and migration notes](docs/operations.md) for Datadog configuration, canary monitor queries, GitLab variables, and schema migration practices. Deployment jobs stay disabled until the owning environment supplies actual rollout and promotion commands and monitor IDs.

The application binds to loopback by default and is intended as a local engineering demonstration. Add authentication, authorization, tenant isolation, secrets management, an audited operator identity, and retention rules before exposing real account data. No compliance certification, real payments, or production deployment is implied.
