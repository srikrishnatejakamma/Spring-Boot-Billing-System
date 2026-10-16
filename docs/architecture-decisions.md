# Architecture decisions

## Runtime and module boundaries

Use Java 21 and Spring Boot 3.5 with one centrally defined version catalogue/BOM. Java 17 is not an additional supported runtime for this implementation. `common-domain` contains framework-independent value types and rules. `common-dto` contains versioned contracts and input validation. Billing and worker are separate executable applications with no dependency on one another's entities or repositories. Integration and platform-support libraries contain shared adapters and infrastructure configuration.

The default billing demo retains local simulated delivery to make the React use case easy to explore. Distributed mode uses independent billing/worker schemas and full event envelopes; it must not silently fall back to the simulator.

## Consistency and processing

The billing service commits account mutations and an outbox together. Confirmed RabbitMQ publication is at least once. The worker first persists an independent inbox receipt, then claims short-lived processing leases. External I/O occurs after the claim transaction commits. The HTTP contract and MongoDB projection enforce monotonic account versions. A crash can repeat work safely; exactly-once transport is not claimed.

Worker results are published durably back to billing. Result revisions and replay generations prevent stale failure/completion messages from overwriting newer state. The worker never opens the billing service's database schema. The billing service never reads the worker's tables.

## Domain use cases

Account onboarding and updates retain idempotency and optimistic locking. Ledger transactions are immutable, contain balanced debit/credit entries, and update a versioned account balance in the same transaction. USD amounts are exact decimals with two fractional digits. A payment cannot exceed outstanding receivables. An account with an outstanding balance cannot be closed.

## Access and configuration

Secured mode validates JWT signatures, expiry, issuer, audience and scopes. Billing reads, billing writes, restricted account fields and event replay have separate scopes. React uses Authorization Code with PKCE; credentials and client secrets are not embedded in the browser bundle. Explicit demo mode is the only anonymous mode.

Spring Cloud Config supplies shared non-secret settings. Secrets come from environment or mounted config trees; the local development OIDC provider is not a production identity service. Central configuration and the OIDC issuer are supplied by the deployment environment.

## Operational boundaries

Resilience controls defer work durably instead of fabricating a successful vendor response. Metrics, JSON logs and trace export support diagnosis. CI verifies code and migrations, scans dependencies/images, and publishes immutable images. Canary tooling prevents promotion and performs rollback on an unhealthy/no-data/error result. Live registry, identity-provider, Datadog and orchestrator verification require the owning environment and are recorded separately.
