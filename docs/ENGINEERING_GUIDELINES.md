# Engineering guidelines

Standards for this repository: **coding**, **testing**, **observability**, and **failure handling**. They align with the architecture in [ARCHITECTURE.md](ARCHITECTURE.md) and the overview in [README.md](../README.md).

---

## Coding standards

### Structure

- **wallet-core:** No Spring annotations. Keep **domain**, **ports** (interfaces), and **adapters** (DynamoDB, EventBridge) in clear packages.
- **serverless-wallet-app:** Thin **handlers** and **controllers**; delegate to **`WalletFacade`**. Avoid embedding business rules in DTOs beyond validation.
- **wallet-lambda:** Keep **routing and serialization** only; no duplicated business logic beyond what is necessary for HTTP mapping.

### Style

- Prefer **immutable** domain events (`record` types) where used.
- **No magic numbers in public API behavior without comment:** pagination defaults live in `WalletFacade` today; prefer **configuration properties** when product requires tunable limits (see `wallet.aws.*` pattern).
- **Configuration over hardcoding:** tables, region, feature flags, event bus name, and event source belong in **YAML** or **environment variables** (already used for Spring; Lambda uses env vars from SAM).

### Dependencies

- **DRY:** Do not copy/paste `WalletFacade` logic into Lambda—call the same class from `wallet-core`.
- **Avoid god classes:** If `WalletFacade` grows too large, extract **domain services** (e.g. transfer policy) rather than adding private methods indefinitely.

### Security and data

- Do **not** log full account numbers or secrets. Prefer **ids** and **correlation ids** in structured logs.
- Validate **inputs** at the edge (Jakarta validation on DTOs in Spring; explicit checks in Lambda router).

---

## Testing philosophy

### Layers

- **Unit tests:** Handlers/facade with **mocks** for `EventPublisher` and `AccountRepository`, or **in-memory** repositories + `InMemoryTransferExecutionService` for realistic transfer behavior.
- **Integration tests:** Spring **`@SpringBootTest`** + **`MockMvc`** for full HTTP → `WalletFacade` → in-memory stack (`wallet.aws.enabled=false` in test profile).

### What to assert

- **Transfers:** Same `transactionId` returns **idempotent replay**; insufficient funds does **not** leave partial state or spurious events.
- **Events:** On success paths, **EventPublisher** receives expected event types (use Mockito `ArgumentCaptor` where appropriate).

### What we do not require in this MVP

- Load tests and chaos tests (document as future work in production-grade analysis).

---

## Observability practices

### Logging

- **Structured narrative:** Log important steps with **key=value** style fields where practical (`transactionId`, `accountId`, balances after transfer—mind PII policies).
- **Correlation:** **`X-Correlation-Id`** → MDC in Spring; Lambda logs **request id** and optional correlation header (see `WalletApiLambdaHandler`).

### Metrics (production direction)

- Count **transfers**, **deposits**, **idempotent replays**, **EventBridge failures**.
- Latency histograms for **Lambda** and **DynamoDB** operations (would integrate with Micrometer/CloudWatch or OTel).

### Tracing

- For production, enable **AWS X-Ray** on API Gateway and Lambda, or **OpenTelemetry** export—**not** fully wired in this MVP; treat as extension point.

---

## Failure handling

### Client-visible errors

- **404:** Account not found (`AccountNotFoundException`).
- **400:** Insufficient funds, validation errors, bad JSON (`IllegalArgumentException`, validation exceptions).
- **500:** Unexpected errors (Lambda returns generic message; avoid leaking stack traces to clients).

### AWS failures

- **DynamoDB `TransactionCanceledException`:** Handled in `DynamoDbTransferExecutionService` with idempotency re-read for races; may surface as insufficient funds when appropriate.
- **EventBridge `PutEvents` failure:** Treated as **hard failure** after write (implementation throws); production might use **outbox pattern** or **retry queues**—document as trade-off.

### Partial failure semantics

- **Transfer:** All-or-nothing via **TransactWriteItems** on AWS path.
- **Event after write:** If EventBridge fails after DynamoDB commit, the system can be **inconsistent** with downstream consumers—production needs **outbox**, **retry**, or **idempotent consumers** (explicit non-goal for MVP).

### Local / in-memory

- **Single JVM lock** for transfers: not distributed; documented as **dev/test only**.

---

## Review checklist (quick)

- [ ] New code respects **port/adapter** boundaries.
- [ ] No new **magic strings** for config (use YAML/env).
- [ ] Tests updated for behavior changes.
- [ ] Logs avoid sensitive data.
- [ ] README or `docs/` updated if behavior or deployment assumptions change.
