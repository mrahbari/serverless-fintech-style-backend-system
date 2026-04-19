# How this project works

**Reading guide:** Use this document to understand **behavior and architecture** end-to-end (Lambda → `wallet-core` → Spring). For a **file- and package-level index** (where each class lives and what it does), see **[IMPLEMENTATION-DETAILS.md](IMPLEMENTATION-DETAILS.md)**. For **run and configure** quickly, see **[../README.md](../README.md)**.

---

This repository is a **multi-module Maven** build: **`wallet-lambda`** (AWS deployable API), **`wallet-core`** (shared domain and AWS logic, no Spring), and **`serverless-wallet-app`** (Spring Boot REST API for local development and optional parity with the Lambda path).

At runtime, **HTTP requests** either hit **API Gateway → Lambda** (`wallet-lambda`) or **Spring MVC** (`serverless-wallet-app`). Both paths call the same **`WalletFacade`** from **`wallet-core`**, wired once to **DynamoDB** and **EventBridge** (or, in the Spring app only, to **in-memory** implementations when AWS is turned off).

The sections below follow that split: **Lambda first**, then **core**, then **Spring app**.

---

## 1. `wallet-lambda` — serverless entrypoint

### Role

`wallet-lambda` is a **single AWS Lambda function** packaged as a **shaded JAR** (fat jar). It implements the AWS Lambda handler interface for **API Gateway HTTP API** proxy events: `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`.

There is **no Spring** in this module. The handler parses the incoming HTTP method, path, query string, and JSON body, then delegates to **`WalletFacade`** in `wallet-core`.

### Request lifecycle

1. **API Gateway** invokes the Lambda with a proxy event (method, path, headers, body, query parameters).
2. **`WalletApiLambdaHandler`** (see `com.example.wallet.lambda`) resolves a **singleton “context”** (`Holder`) on first use:
   - Builds **AWS SDK v2** `DynamoDbClient` and `EventBridgeClient` (region from `AWS_REGION`, credentials from the Lambda execution role via `DefaultCredentialsProvider`).
   - Optionally applies **`AWS_ENDPOINT_URL`** as an endpoint override (useful for **LocalStack** or similar).
   - Constructs **`EventBridgeEventPublisher`, `DynamoDbAccountRepository`, `DynamoDbTransferExecutionService`**, and finally **`WalletFacade`** with the same wiring as the Spring app when AWS is enabled.
3. Each invocation calls **`route(...)`**, which matches **normalized path + HTTP method** to a `WalletFacade` operation and returns JSON with appropriate **HTTP status** (201 for create account, 200 for others, 404/400/500 on errors).

### Path normalization

`normalizePath` strips a leading slash and, when present, a **single stage-like prefix** (for example `prod/`, `dev/`, `$default/`) so that routes like `/accounts` and `/prod/accounts` both map to the internal key `accounts`.

### Routing table (API parity with the Spring app)

| Method | Normalized path | Notes |
|--------|-----------------|--------|
| `GET` | `accounts` | Query: `limit`, `cursor` — list accounts |
| `GET` | `accounts/{id}` | Path segment after `accounts/` is the account id |
| `POST` | `accounts` | JSON body: optional `initialBalance` |
| `POST` | `deposit` | JSON: `accountId`, `amount` |
| `POST` | `transfer` | JSON: `transactionId`, `fromAccountId`, `toAccountId`, `amount` |

Unknown paths return **404** JSON. Domain errors map to **404** (`AccountNotFoundException`), **400** (`InsufficientFundsException`, `IllegalArgumentException`), or **500** for unexpected failures.

### Logging and correlation

The handler logs `method`, `normalized path`, and a **correlation id** taken from `X-Correlation-Id` / `x-correlation-id` when present, otherwise **Lambda request id**.

### Environment variables (Lambda)

| Variable | Purpose |
|----------|---------|
| `ACCOUNTS_TABLE` | DynamoDB table name for accounts (required) |
| `IDEMPOTENCY_TABLE` | DynamoDB table for transfer idempotency records (required) |
| `EVENT_BUS_NAME` | EventBridge bus (default `default`) |
| `EVENT_SOURCE` | Event `source` field (default `wallet.wallet`) |
| `AWS_REGION` | Region for SDK clients (default `eu-west-1` if unset) |
| `AWS_ENDPOINT_URL` | Optional: custom endpoint for local/cloud emulators |

### SAM / deployment (`template.yaml`)

- **Runtime:** Java 21 (`java21`), **x86_64**.
- **Resources:** two **on-demand DynamoDB** tables (accounts keyed by `accountId`, idempotency keyed by `transactionId`), one **Lambda** with **IAM** allowing CRUD on both tables and **`events:PutEvents`** (default event bus).
- **Environment:** `template.yaml` injects table names and EventBridge settings into the function.
- **HTTP API:** `ANY` on `/{proxy+}` so all routes go to this function.

**Build before deploy:** produce the Lambda artifact with Maven, for example:

`mvn -pl wallet-lambda -am package`

Then `sam deploy` (or `sam deploy --guided` the first time) using `samconfig.toml` as needed. The template expects the shaded jar at `wallet-lambda/target/wallet-lambda-1.0.0-SNAPSHOT.jar`.

---

## 2. `wallet-core` — shared logic (no Spring)

### Role

`wallet-core` is a **plain Java library** used by both `wallet-lambda` and `serverless-wallet-app`. It holds:

- **Domain:** `Account`, `TransferResult`.
- **Exceptions:** `AccountNotFoundException`, `InsufficientFundsException`.
- **Events:** `DomainEvent` and concrete events (`AccountCreatedEvent`, `DepositCompletedEvent`, `TransferCompletedEvent`), plus **`EventPublisher`**.
- **API-style view models:** `AccountSummary`, `AccountListResponse`, `CreateAccountResult`, `DepositResult` (JSON-friendly shapes for list/get/create/deposit).
- **Persistence:** `AccountRepository`, `AccountPage`, and **`DynamoDbAccountRepository`** (AWS SDK v2 DynamoDB API).
- **Transfers:** `TransferExecutionService` and **`DynamoDbTransferExecutionService`** (atomic `TransactWriteItems` + idempotency row + EventBridge publish on success).
- **Integration:** **`EventBridgeEventPublisher`** (serializes event detail and calls `PutEvents`).
- **Facade:** **`WalletFacade`** — the single place for “create account, deposit, transfer, list, get” with validation and logging, independent of Spring or Lambda.

### How data moves

- **Accounts** are stored as DynamoDB items with attributes `accountId` and `balance`.
- **Listing** uses a **scan** (demo-style) and in-memory sort/cursor paging; production systems would typically use a different access pattern.
- **Transfers** use **one transaction** to debit, credit, and **conditionally put** an idempotency record keyed by `transactionId`; conflicts or insufficient funds are handled per `DynamoDbTransferExecutionService`.
- **Events** are emitted to **EventBridge** after successful side effects (create, deposit, transfer), not as in-process pub/sub.

### Dependencies

AWS SDK v2 (`dynamodb`, `eventbridge`), Jackson, SLF4J API — no Spring Boot dependencies.

---

## 3. `serverless-wallet-app` — Spring Boot API

### Role

`serverless-wallet-app` is a **Spring Boot** application that exposes the same wallet operations as **REST** endpoints. It is ideal for **local runs** and **tests** without AWS, and can talk to **real DynamoDB + EventBridge** when configured.

### Configuration (`wallet.aws.enabled`)

- **`false` (default):** `InMemoryAccountRepository`, **`InMemoryTransferExecutionService`** (JVM-local idempotency with a lock), and **`LoggingEventPublisher`** (logs events instead of EventBridge). No AWS credentials required.
- **`true`:** **`AwsClientConfiguration`** registers `DynamoDbClient` and `EventBridgeClient` (region, optional endpoint override from properties). **`WalletAwsServiceConfiguration`** supplies **DynamoDB-backed** `AccountRepository`, `EventPublisher`, and `TransferExecutionService` beans, same classes as Lambda uses from `wallet-core`.

Properties are under `wallet.aws` in `application.yml` (and can be overridden with env vars such as `WALLET_AWS_ENABLED`).

### Wiring

- **`WalletFacadeConfiguration`** defines a **`WalletFacade`** bean from `AccountRepository`, `EventPublisher`, and `TransferExecutionService`.
- **Handlers** (`CreateAccountHandler`, `DepositHandler`, `TransferHandler`, `ListAccountsHandler`, `GetAccountHandler`) are thin **Spring components** that delegate to **`WalletFacade`** (mirroring “one Lambda per use case” in `task.md`, but in-process).
- **`WalletController`** maps HTTP to those handlers; **`GlobalExceptionHandler`** maps domain exceptions to HTTP status codes.
- **`CorrelationIdFilter`** adds **MDC** correlation for logs.

### Tests

Unit tests often build **`WalletFacade`** with mocks or in-memory repositories; **`TransferFlowIntegrationTest`** exercises the full HTTP flow with the default in-memory profile.

---

## End-to-end picture

| Concern | `wallet-lambda` | `wallet-core` | `serverless-wallet-app` |
|--------|-------------------|---------------|---------------------------|
| HTTP | API Gateway Lambda proxy handler | — | Spring MVC |
| Business rules | Via `WalletFacade` | `WalletFacade` + services | Via `WalletFacade` |
| AWS data path | Always DynamoDB + EventBridge | Same implementations | When `wallet.aws.enabled=true` |
| Local / no AWS | — | In-memory impls only in Spring module | `wallet.aws.enabled=false` |

Together, **`wallet-lambda`** is the **deployable serverless surface**, **`wallet-core`** is the **shared engine**, and **`serverless-wallet-app`** is the **developer-friendly Spring** twin that reuses that engine.
