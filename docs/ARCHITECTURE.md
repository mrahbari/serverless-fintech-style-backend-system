# Architecture

This document describes **system design**, **trade-offs**, and **scaling** for the Serverless Wallet MVP. It complements the operational detail in [README-HOW-IT-WORKS.md](README-HOW-IT-WORKS.md).

---

## System design

### Logical view

The system is a **wallet API** that supports:

- Account lifecycle (create, read, list with cursor pagination)
- Deposits (atomic balance increase)
- Transfers between accounts with **idempotency** on `transactionId`
- **Domain events** emitted after successful writes (EventBridge or log-only)

**Core rule:** Business rules live in **`wallet-core`** (`WalletFacade` + ports). **Transport** is either:

- **AWS:** API Gateway → **Lambda** (`wallet-lambda`) → `wallet-core` → DynamoDB + EventBridge, or  
- **Local:** Spring MVC → same `wallet-core` → in-memory implementations.

### Component responsibilities

| Component | Responsibility |
|-----------|----------------|
| **HTTP edge** | Parse requests, validate DTOs (Spring), or parse proxy events (Lambda); map errors to HTTP status. |
| **WalletFacade** | Orchestrate use cases; validate inputs; call repositories and transfer service; publish events after success. |
| **AccountRepository** | Persistence abstraction for accounts (DynamoDB or memory). |
| **TransferExecutionService** | Transfer + idempotency semantics (DynamoDB transactions or in-memory lock). |
| **EventPublisher** | Emit domain events (EventBridge `PutEvents` or structured log). |

### Data stores (AWS path)

- **Accounts table:** Partition key `accountId`; attribute `balance`.
- **Idempotency table:** Partition key `transactionId`; stores outcome of completed transfers for replay.

### Event flow

Successful writes produce **immutable domain events** with a stable `type()` (maps to EventBridge `detail-type`). Consumers are **not** embedded; the intended model is **EventBridge rules → downstream Lambdas** (notifications, compliance, metrics).

---

## Trade-offs

### Single Lambda vs many Lambdas

**Current:** One Lambda JAR handles all routes via internal routing.

- **Pros:** Simpler deployment, one IAM role, one cold-start artifact to reason about for an MVP.
- **Cons:** Blast radius and scaling granularity are coarser than per-route Lambdas; routing code must stay disciplined.

### DynamoDB list implementation

**Current:** Scan (paginated) + in-memory sort + cursor window.

- **Pros:** Minimal schema, easy to explain in interviews.
- **Cons:** O(table size) work for listing; **not** suitable for very large tables. Production would introduce a **GSI**, **sort key** design, or a separate read model.

### JVM Lambda

- **Pros:** Shared **`wallet-core`** JAR, official AWS SDK v2, mature ecosystem.
- **Cons:** Cold start and package size vs Go/Rust/custom runtime; acceptable for many API workloads with provisioned concurrency if needed later.

### Spring alongside Lambda

- **Pros:** Fast local feedback, `MockMvc`, test profile without cloud.
- **Cons:** Two ways to expose HTTP; documentation must keep them aligned (same `WalletFacade` mitigates drift).

### Idempotency storage

- **Pros:** DynamoDB conditional writes align with **exactly-once transfer semantics** from the client’s perspective (same `transactionId` → same result).
- **Cons:** Extra table and latency vs idempotency purely in application memory (not acceptable in Lambda without shared store).

---

## Scaling approach

### Request path

- **API Gateway + Lambda:** Scales with concurrent invocations (account limits apply). Use **reserved concurrency** or **provisioned concurrency** if tail latency from cold start is unacceptable.
- **DynamoDB:** Template uses **on-demand** billing; for predictable traffic, switch to **provisioned** capacity or **auto scaling** after measuring RCU/WCU.

### Data growth

- **Accounts:** Partition per `accountId` scales horizontally; hot keys remain a domain concern (not addressed in this MVP).
- **Listing:** Replace scan-based listing before high cardinality (see trade-offs).

### Events

- **EventBridge** default bus quotas apply (TPS, payload size). For very high throughput, **partitioning** or **SQS buffering** might sit between wallet and consumers (out of scope here).

### Observability at scale

- **CloudWatch Logs** with structured fields (correlation id, `transactionId`, account ids—avoid logging full PII in production).
- **Metrics:** Custom metrics on transfer success/failure, idempotent replays, EventBridge `PutEvents` failures.
- **Tracing:** AWS X-Ray or OpenTelemetry from Lambda and downstream (not wired in this repo; see [ENGINEERING_GUIDELINES.md](ENGINEERING_GUIDELINES.md)).

---

## Alignment with design principles

- **Separation of concerns:** Transport (Lambda/Spring) ≠ `WalletFacade` ≠ persistence ≠ events.
- **No god service:** Facade orchestrates; it does not embed SQL or low-level SDK calls—those stay in repository/service classes.
- **Extensibility:** New persistence or event backends add implementations of ports without changing `WalletFacade` signatures (strategy-style substitution via Spring beans or manual wiring in Lambda).
