# Serverless Wallet (MVP)

A **Java 21** wallet backend with **optional AWS** (DynamoDB, EventBridge) and a **SAM-deployable Lambda** path. The same business logic runs in **`wallet-core`**; **Spring Boot** (`serverless-wallet-app`) is for local development and parity testing; **`wallet-lambda`** is the serverless HTTP entrypoint in AWS.

**Deeper docs:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/ENGINEERING_GUIDELINES.md](docs/ENGINEERING_GUIDELINES.md) · [docs/README-HOW-IT-WORKS.md](docs/README-HOW-IT-WORKS.md) · [docs/IMPLEMENTATION-DETAILS.md](docs/IMPLEMENTATION-DETAILS.md)

---

## Features

- **Account Management**: Create and retrieve accounts with balance tracking.
- **Cursor Pagination**: List accounts efficiently using cursor-based pagination.
- **Atomic Deposits**: Securely deposit funds into accounts.
- **Idempotent Transfers**: Transfer funds between accounts with guaranteed idempotency using `transactionId`.
- **Event-Driven**: Publishes domain events (`AccountCreated`, `DepositCompleted`, `TransferCompleted`) to EventBridge (AWS) or logs (Local).
- **Dual Runtime**: Run as a **Spring Boot** application for local dev or as a **Single-purpose Lambda** for production.
- **Cloud Native**: Built-in support for DynamoDB and EventBridge.

---

## Architecture overview

```text
                    ┌─────────────────────┐
                    │   API Gateway       │
                    │   (HTTP API)        │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │    wallet-lambda    │  ← Java 21, no Spring
                    │WalletApiLambdaHandler
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────────────────────┐
                    │         wallet-core                 │
                    │ WalletFacade                        │
                    │ AccountRepository (DynamoDB / mem)  │
                    │ TransferExecutionService            │
                    │ EventPublisher → EventBridge / log  │
                    └──────────┬──────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                 │
     ┌────────▼────────┐              ┌───────▼────────┐
     │ Amazon DynamoDB │              │  EventBridge   │
     │ (accounts +     │              │ (domain events)│
     │  idempotency)   │              └────────────────┘
     └─────────────────┘

  Local alternative: serverless-wallet-app (Spring MVC) → same WalletFacade
```

**Modules**

| Module | Responsibility |
|--------|----------------|
| **wallet-core** | Domain, ports (`AccountRepository`, `EventPublisher`, `TransferExecutionService`), `WalletFacade`, DynamoDB and EventBridge implementations. **No Spring.** |
| **wallet-lambda** | Single Lambda handler: HTTP routing, JSON, exception → status mapping. Wires AWS SDK clients from environment. |
| **serverless-wallet-app** | REST API, validation, correlation ID filter, **in-memory** or **AWS** wiring via `wallet.aws.enabled`. |

**Design principles (enforced in structure)**

- **Separation of concerns:** HTTP (Spring or Lambda) ≠ persistence ≠ transfer mechanics ≠ event emission.
- **No god service:** `WalletFacade` orchestrates use cases; persistence and transfers are separate types (`AccountRepository`, `TransferExecutionService`).
- **Clean layering:** API → facade → repositories/services → AWS SDK (or in-memory substitutes).
- **Extensibility:** Ports are interfaces; `DynamoDb*` vs `InMemory*` are **strategies** selected by configuration, not `if` chains in business logic.
- **DRY:** One `WalletFacade` shared by Lambda and Spring.
- **Configuration:** Tables, region, event bus, feature flag live in **`application.yml`** / environment (see below). Remaining literals (e.g. default page size inside `WalletFacade`) are candidates to externalize if product requirements demand it.

---

## Why serverless-first

- **Operational model:** Pay per request, no always-on JVM cluster for this MVP; **Lambda + HTTP API** match interview and real AWS narratives.
- **Scaling:** API Gateway and Lambda scale with traffic; DynamoDB uses on-demand billing in the sample **SAM** template.
- **Boundary clarity:** Forces **stateless** handlers and **external** state (DynamoDB) and **async fan-out** (EventBridge), which matches how production fintech-style systems are often drawn on a whiteboard.
- **Same code path:** `wallet-core` keeps domain logic **deployable** as a fat JAR on Lambda without dragging Spring.

Spring is included **deliberately** for developer velocity: run and test locally without AWS, while production-style deployment remains Lambda-centric.

---

## Event-driven explanation

- **Not** an in-process pub/sub bus. After successful writes, the app publishes **domain events** (`AccountCreatedEvent`, `DepositCompletedEvent`, `TransferCompletedEvent`) through the **`EventPublisher`** port.
- **With AWS:** `EventBridgeEventPublisher` calls **`events:PutEvents`**. Downstream **rules** (not in this repo) would target other Lambdas (notifications, analytics, fraud)—the pattern is **decoupled consumers**.
- **Without AWS:** `LoggingEventPublisher` records the same events for **observability** and tests.

This mirrors **EventBridge** as the integration bus instead of coupling listeners inside the wallet service.

---

## Trade-offs

| Choice | Benefit | Cost |
|--------|---------|------|
| Single Lambda for all routes | One artifact, simple IAM and SAM | Cold start affects every route; less isolation than one Lambda per route |
| Full table scan + sort for listing | Simple code, fine for demos | Does not scale to huge tables; production would use GSI/query patterns |
| JVM on Lambda | Mature AWS SDK v2, shared `wallet-core` | Larger package, cold start vs native runtimes |
| Spring for local only | Fast feedback, MockMvc tests | Two HTTP stacks to mentally map (Spring vs Lambda router) |
| Idempotency via DynamoDB `transactionId` | Safe retries | Extra table and conditional writes |
| `WalletFacade` as orchestrator | Clear use cases | Must stay thin—logic belongs in services/repositories |

---

## Getting Started

### Prerequisites
- **Java 21**
- **Maven 3.9+**
- **AWS SAM CLI** (optional, for Lambda deployment)
- **LocalStack** (optional, for local AWS emulation)

### How to run locally

**1. Default (In-Memory, No AWS)**
Run the Spring Boot application using the default profile:
```bash
mvn -pl serverless-wallet-app spring-boot:run
```
The API will be available at: **http://localhost:8080**

**2. AWS-Backed (LocalStack or Real AWS)**
Set `WALLET_AWS_ENABLED=true` and configure your environment:
```bash
# Example with custom endpoint (LocalStack)
export WALLET_AWS_ENABLED=true
export AWS_ENDPOINT_OVERRIDE=http://localhost:4566
mvn -pl serverless-wallet-app spring-boot:run
```
See [docs/AWS_SETUP.md](docs/AWS_SETUP.md) for table creation and credentials.

**3. Running Tests**
Run all tests across the project:
```bash
mvn test
```
*Note: Integration tests in `serverless-wallet-app` use the `test` profile with `wallet.aws.enabled=false` by default.*

---

## Lambda & SAM Deployment

**Build the Lambda JAR:**
```bash
mvn -pl wallet-lambda -am package
```

**Deploy using SAM:**
```bash
sam deploy --guided
```
This uses [template.yaml](template.yaml) to provision DynamoDB tables, EventBridge configurations, and the Lambda function.

---

## API Examples

Optional header: **`X-Correlation-Id`** (logged via MDC; useful for tracing).

**Create Account**
```bash
curl -s -X POST http://localhost:8080/accounts \
  -H "Content-Type: application/json" \
  -d '{"initialBalance":100}'
```

**List Accounts (Cursor Pagination)**
```bash
curl -s "http://localhost:8080/accounts?limit=20"
curl -s "http://localhost:8080/accounts?limit=20&cursor=<nextCursor>"
```

**Get Account**
```bash
curl -s http://localhost:8080/accounts/{accountId}
```

**Deposit**
```bash
curl -s -X POST http://localhost:8080/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountId":"<id>","amount":"10.00"}'
```

**Transfer (Idempotent)**
```bash
curl -s -X POST http://localhost:8080/transfer \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-001","fromAccountId":"<a>","toAccountId":"<b>","amount":"25.00"}'
```

---

## Configuration

Primary file: **`serverless-wallet-app/src/main/resources/application.yml`**

| Key / Env | Purpose | Default |
|-----------|---------|---------|
| `wallet.aws.enabled` / `WALLET_AWS_ENABLED` | Toggle AWS adapters | `false` |
| `wallet.aws.region` / `AWS_REGION` | AWS Region | `eu-west-1` |
| `wallet.aws.endpoint-override` / `AWS_ENDPOINT_OVERRIDE` | Custom AWS endpoint | - |
| `wallet.aws.accounts-table` / `WALLET_ACCOUNTS_TABLE` | DynamoDB accounts table | `WalletAccounts` |
| `wallet.aws.idempotency-table` / `WALLET_IDEMPOTENCY_TABLE` | Idempotency table | `WalletIdempotency` |
| `wallet.aws.event-bus-name` / `WALLET_EVENT_BUS_NAME` | EventBridge bus | `default` |

---

## Documentation Map

| Document | Purpose |
|----------|---------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, trade-offs, scaling |
| [docs/LAMBDA-GUIDE-FA.md](docs/LAMBDA-GUIDE-FA.md) | Beginner-friendly Lambda & Stateless guide (Persian) |
| [docs/ENGINEERING_GUIDELINES.md](docs/ENGINEERING_GUIDELINES.md) | Standards, testing, observability, failures |
| [docs/README-HOW-IT-WORKS.md](docs/README-HOW-IT-WORKS.md) | Module-by-module behavior |
| [docs/IMPLEMENTATION-DETAILS.md](docs/IMPLEMENTATION-DETAILS.md) | Package and class index |
| [docs/AWS_SETUP.md](docs/AWS_SETUP.md) | Tables and LocalStack setup |
| [docs/PRODUCTION-GRADE-ANALYSIS.md](docs/PRODUCTION-GRADE-ANALYSIS.md) | Production readiness review |

