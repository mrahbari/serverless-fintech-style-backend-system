# Production-grade analysis — completed run

This document is a **filled instance** of [PRODUCTION-GRADE-ANALYSIS_TEMPLATE.md](PRODUCTION-GRADE-ANALYSIS_TEMPLATE.md), produced by a senior-backend-style review of the `serverless-mvp2` codebase (structure, AWS alignment, risks). For a repeatable review process, use [MCP_REVIEW_PROMPT.md](MCP_REVIEW_PROMPT.md).

---

## Metadata

| Field | Value |
|-------|--------|
| **Date** | 2026-04-19 |
| **Author / reviewer** | Cursor agent (automated architecture pass) |
| **Repository / branch / commit** | N/A (workspace not a git repository at review time) |
| **Scope** | Full repo: `wallet-core`, `wallet-lambda`, `serverless-wallet-app`, `template.yaml`, `docs/` |

---

## Executive summary

- **Strengths:** Clear **hexagonal-style** split (`wallet-core` ports + adapters), **single `WalletFacade`** shared by Lambda and Spring, **idempotent transfers** on DynamoDB via `TransactWriteItems` + idempotency table, **domain events** abstracted behind `EventPublisher`, **config externalized** via `application.yml` and Lambda env vars.
- **P0 for real production:** **No authentication or authorization** on the HTTP API; **EventBridge `PutEvents` scoped to `Resource: '*'`** in SAM template (overly broad); **no outbox** if EventBridge fails after DynamoDB commit (downstream inconsistency risk).
- **P1:** **Account listing** uses **scan + sort** in `DynamoDbAccountRepository` — acceptable for **demo/interview** scale, **not** for large tables without **GSI/query redesign**.
- **P1:** **Pagination limits** (`DEFAULT_LIMIT=20`, `MAX_LIMIT=100`) are **hardcoded** in `WalletFacade` — should move to **configuration** for production tuning.
- **P2:** **Single Lambda** routes all HTTP — fine for MVP; **per-route Lambdas** or **stricter IAM** per function would be a later **operational** refinement.
- **Go/no-go:** **Go** for **MVP / interview / learning**; **no-go** for **public internet production** without **AuthN** + **WAF/rate limits** + **narrower IAM** + **operational** metrics/alerts.

---

## Architecture

| Topic | Assessment | Notes |
|-------|------------|------------|
| Module boundaries | **OK** | `wallet-core` has no Spring; Lambda and Spring app are thin transports. |
| Serverless deployment model | **OK with caveats** | `WalletApiLambdaHandler` builds SDK clients once; `template.yaml` defines tables + Lambda + HTTP API. |
| Event-driven integration | **OK** | Events emitted after successful writes; **EventBridge** is the intended bus; **no** in-process `EventBus`. |

---

## Data and persistence

| Topic | Assessment | Notes |
|-------|------------|------------|
| DynamoDB schema | **OK for MVP** | PK `accountId` / `transactionId`; on-demand billing in SAM. |
| Access patterns | **Concern** | `findAccountsAfterCursor` on DynamoDB path **scans** full table (paginated) then sorts — **O(n)** memory per request for large tables. |
| Idempotency | **OK** | Transfer `transactionId` stored in idempotency table; **conditional put** in same transaction as debit/credit. |
| Listing / scan trade-offs | **Gap for scale** | Documented in README as **demo**; production needs **GSI** or **separate read model**. |

---

## Security

| Topic | Assessment | Notes |
|-------|------------|--------|
| Secrets / credentials | **OK for pattern** | Lambda uses **IAM role**; Spring uses **default credential chain**; no secrets in repo. |
| Logging / PII | **Concern** | `WalletApiLambdaHandler` logs method/path/correlation; **balances and account ids** can appear in logs — **mask** or **sample** in production. |
| AuthN / AuthZ (if any) | **Gap** | **None** on REST or Lambda — **must** add API keys, JWT, or IAM before public deployment. |

---

## Reliability and consistency

| Topic | Assessment | Notes |
|-------|------------|--------|
| Transfer atomicity | **OK (AWS path)** | DynamoDB **TransactWriteItems** for debit, credit, idempotency row. |
| Event publish after commit | **Concern** | `EventBridgeEventPublisher` runs **after** transaction commit; **fail on PutEvents** throws — **no** retry/outbox; risk of **written transfer** but **no event** (or handler failure). |
| Failure modes (partial writes, EventBridge) | **Concern** | `InMemoryTransferExecutionService` uses **single JVM lock** — **not** distributed; documented as **dev/test**. |

---

## Observability

| Topic | Assessment | Notes |
|-------|------------|--------|
| Logging | **Partial** | `CorrelationIdFilter` + MDC in Spring; Lambda logs **request id** + optional header. **Structured JSON** to CloudWatch is a **future** step. |
| Metrics | **Gap** | No **Micrometer** or **custom CloudWatch metrics** on Lambda in repo. |
| Tracing | **Gap** | **X-Ray / OTel** not wired; **ENGINEERING_GUIDELINES** mentions as extension. |

---

## Testing

| Topic | Assessment | Notes |
|-------|------------|--------|
| Coverage of critical paths | **Partial** | `TransferHandlerTest` (idempotency, insufficient funds), `TransferFlowIntegrationTest` (HTTP), handler unit tests with mocks. |
| Gaps | **Notable** | **No** contract tests against **real DynamoDB** in CI; **no** load tests; **Lambda handler** has **no** dedicated unit tests (routing/normalizePath). |

---

## Prioritized recommendations

| ID | Priority | Recommendation | Owner | Target date |
|----|----------|----------------|-------|-------------|
| R1 | P0 | Add **authentication** (API Gateway authorizer or Spring Security) before any public deployment. | TBD | |
| R2 | P0 | Restrict IAM for **`events:PutEvents`** to **default event bus ARN** (or `*` only if required by platform) instead of `Resource: '*'` where possible. | TBD | |
| R3 | P1 | Externalize **list default/max limits** from `WalletFacade` to `application.yml` / Lambda env. | TBD | |
| R4 | P1 | Introduce **outbox** or **retry queue** for EventBridge failures **or** document **acceptance** of at-least-once downstream with **idempotent consumers**. | TBD | |
| R5 | P2 | Add **unit tests** for `WalletApiLambdaHandler.normalizePath` and **error status** mapping. | TBD | |
| R6 | P2 | Replace **scan-based listing** with **GSI** or **bounded** query when table size grows. | TBD | |

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Unauthenticated API abuse | High if exposed | High | AuthN + rate limiting + WAF |
| Broad EventBridge IAM | Medium | Medium | Scope `Resource` to bus ARN |
| Event loss after DB commit | Low–Medium | Medium | Outbox, retries, idempotent consumers |
| Scan-based listing cost/latency | High at scale | High | Redesign access pattern |
| Log leakage of financial data | Medium | Medium | Redact fields, structured logging policy |

---

## Follow-up

- [ ] Re-run this analysis after **auth** and **IAM** hardening.
- [ ] Link CI **test** results to this doc when `mvn verify` is run on every PR.

---

## Appendix — raw notes

- **Files reviewed:** `wallet-core` (`WalletFacade`, `DynamoDb*` , `EventBridgeEventPublisher`), `wallet-lambda` (`WalletApiLambdaHandler`), `serverless-wallet-app` (config, handlers), `template.yaml`.
- **Related docs:** [ARCHITECTURE.md](ARCHITECTURE.md), [ENGINEERING_GUIDELINES.md](ENGINEERING_GUIDELINES.md), [README.md](../README.md).
