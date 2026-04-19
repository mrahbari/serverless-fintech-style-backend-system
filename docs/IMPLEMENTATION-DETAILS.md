# Implementation details — Serverless Wallet MVP

This file is the **package- and file-level catalog**: what lives where, what each piece does, and how it maps to **AWS** (Lambda, API Gateway, DynamoDB, EventBridge).

**If you want the narrative “how requests flow” across modules first**, read **[README-HOW-IT-WORKS.md](README-HOW-IT-WORKS.md)**. Use *this* document when you are **navigating the codebase** or preparing for **interview-style “where is X implemented?”** questions.

| Document | Best for |
|----------|----------|
| **[README.md](../README.md)** | Quick start, run commands, configuration overview |
| **[README-HOW-IT-WORKS.md](README-HOW-IT-WORKS.md)** | Architecture, module boundaries, Lambda vs Spring, SAM deploy |
| **IMPLEMENTATION-DETAILS.md** (this file) | Class/package map, AWS mapping table, tests |
| **[AWS_SETUP.md](AWS_SETUP.md)** | Tables, LocalStack, credentials |
| **[task.md](../task.md)** | Original exercise brief |

---

## Multi-module layout

| Module | Artifact | Role |
|--------|----------|------|
| **wallet-parent** | `pom.xml` (packaging `pom`) | Spring Boot parent, Java 21, AWS SDK BOM |
| **wallet-core** | `wallet-core` | Domain, events, `WalletFacade`, DynamoDB + EventBridge implementations — **no Spring** |
| **wallet-lambda** | `wallet-lambda` | Shaded JAR, `WalletApiLambdaHandler` → API Gateway proxy — **no Spring** |
| **serverless-wallet-app** | `serverless-wallet` | Spring Boot REST app; depends on `wallet-core`; in-memory or AWS via `wallet.aws.enabled` |

Shared use cases are implemented once in **`WalletFacade`** (`wallet-core`). **Lambda** and **Spring** both call it; only the **wiring** differs (manual in Lambda, beans in Spring).

---

## A) Cloud picture (unchanged concepts)

| Piece in code | In AWS |
|---------------|--------|
| **`WalletApiLambdaHandler`** (wallet-lambda) | **Lambda** + **API Gateway** (HTTP API proxy) |
| **`WalletController`** + handlers (serverless-wallet-app) | Same logical routes as API Gateway → often used for **local dev** |
| **`WalletFacade`** | Application service (not a managed AWS service; runs *inside* Lambda or Spring) |
| **`EventBridgeEventPublisher`** | **`events:PutEvents`** (`eventBusName`, `source`, `detailType`, JSON `detail`) |
| **`LoggingEventPublisher`** | Local substitute: log only, no `PutEvents` |
| **`DynamoDbAccountRepository`** | DynamoDB table: PK `accountId`, `balance` |
| **`DynamoDbTransferExecutionService`** | **`TransactWriteItems`**: debit, credit, conditional idempotency **Put** on idempotency table (`transactionId` PK) |
| **`InMemory*`** | Same contracts without AWS; transfer uses JVM lock + maps |
| **`CorrelationIdFilter`** | Same idea as **`X-Correlation-Id`** through API Gateway into logs / **CloudWatch** |

---

## B) Root `pom.xml`

- **Parent:** `spring-boot-starter-parent` (version aligned with the repo).
- **Properties:** `java.version` (21), `aws.sdk.version` (BOM for SDK v2).
- **Modules:** `wallet-core`, `serverless-wallet-app`, `wallet-lambda`.

---

## C) `wallet-core` (`com.example.wallet`)

| Package / type | Responsibility |
|----------------|----------------|
| **`domain`** | `Account`, `TransferResult` |
| **`exception`** | `AccountNotFoundException`, `InsufficientFundsException` |
| **`event`** | `DomainEvent`, `EventPublisher`, concrete events (`AccountCreatedEvent`, `DepositCompletedEvent`, `TransferCompletedEvent`) |
| **`view`** | `AccountSummary`, `AccountListResponse`, `CreateAccountResult`, `DepositResult` (JSON-friendly responses) |
| **`repository`** | `AccountRepository`, `AccountPage`, **`DynamoDbAccountRepository`** |
| **`service`** | `TransferExecutionService`, **`DynamoDbTransferExecutionService`** |
| **`aws`** | **`EventBridgeEventPublisher`** |
| **`facade`** | **`WalletFacade`** — create account, deposit, transfer, list, get; validation + logging; calls `EventPublisher` after writes |

**AWS:** DynamoDB access and EventBridge `PutEvents` are implemented here. **No** Spring annotations.

---

## D) `wallet-lambda` (`com.example.wallet.lambda`)

| File | Responsibility |
|------|----------------|
| **`WalletApiLambdaHandler`** | `RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>`: normalize path, route by method + path, JSON body parsing, map exceptions to HTTP status, build **`WalletFacade`** once per runtime (cold start) with env-driven table names |

**Environment:** `ACCOUNTS_TABLE`, `IDEMPOTENCY_TABLE`, `EVENT_BUS_NAME`, `EVENT_SOURCE`, `AWS_REGION`, optional `AWS_ENDPOINT_URL`.

**Deploy:** see **`template.yaml`**, **`samconfig.toml`**; build with `mvn -pl wallet-lambda -am package`.

---

## E) `serverless-wallet-app` (`com.example.wallet`)

### `WalletApplication.java`

`@SpringBootApplication`, `@EnableConfigurationProperties(WalletAwsProperties.class)`.

### `config`

| Class | Responsibility |
|-------|----------------|
| **`WalletAwsProperties`** | `wallet.aws.*` — enabled, region, endpoint override, table names, event bus, event source |
| **`AwsClientConfiguration`** | `@ConditionalOnProperty(wallet.aws.enabled=true)` — `DynamoDbClient`, `EventBridgeClient` |
| **`WalletAwsServiceConfiguration`** | When AWS on: beans for **`DynamoDbAccountRepository`**, **`EventBridgeEventPublisher`**, **`DynamoDbTransferExecutionService`** |
| **`WalletFacadeConfiguration`** | **`WalletFacade`** bean from `AccountRepository` + `EventPublisher` + `TransferExecutionService` |
| **`CorrelationIdFilter`** | `X-Correlation-Id` / UUID → MDC + response header |

### `api`

| Class | Responsibility |
|-------|----------------|
| **`WalletController`** | REST mapping to handler beans |
| **`GlobalExceptionHandler`** | 404 / 400 for domain and validation errors |
| **`api/dto/*`** | `CreateAccountRequest`, `DepositRequest`, `TransferRequest` (Jakarta validation) |

### `handler`

Thin **Spring `@Component`s** that delegate to **`WalletFacade`** (same as Lambda behavior, different transport).

| Class | Delegates to `WalletFacade` |
|-------|-----------------------------|
| `CreateAccountHandler` | `createAccount` |
| `DepositHandler` | `deposit` |
| `TransferHandler` | `transfer` |
| `ListAccountsHandler` | `listAccounts` |
| `GetAccountHandler` | `getAccount` |

### `repository`

| Class | When |
|-------|------|
| **`InMemoryAccountRepository`** | `wallet.aws.enabled=false` (default) |

### `service`

| Class | When |
|-------|------|
| **`InMemoryTransferExecutionService`** | `wallet.aws.enabled=false` (default) |

### `aws`

| Class | When |
|-------|------|
| **`LoggingEventPublisher`** | `wallet.aws.enabled=false` (default) |

### Resources

- **`serverless-wallet-app/src/main/resources/application.yml`** — `wallet.aws.*`, logging pattern with `correlationId`.
- **`serverless-wallet-app/src/test/resources/application-test.yml`** — typically `wallet.aws.enabled=false`.

---

## F) Tests

- **Unit tests** — mock `EventPublisher` / `AccountRepository` or build **`WalletFacade`** with **`InMemoryAccountRepository`** + **`InMemoryTransferExecutionService`**.
- **`TransferFlowIntegrationTest`** — `@SpringBootTest` + `MockMvc`, full HTTP transfer + deposit flow (in-memory profile).

---

## G) Lambda / interview cheat sheet

1. **`WalletApiLambdaHandler`** ≈ API Gateway **Lambda** integration.  
2. **`WalletFacade`** ≈ shared **business** layer inside Lambda or Spring.  
3. **`DynamoDbTransferExecutionService`** ≈ **`TransactWriteItems`** + idempotency **Put**.  
4. **`EventBridgeEventPublisher`** ≈ **`events:PutEvents`**; **rules** → downstream Lambdas.  
5. **Handlers** in Spring ≈ **one concern per class**, analogous to splitting Lambdas by route in larger systems.

---

*Aligned with the multi-module `serverless-mvp2` layout (`wallet-core`, `wallet-lambda`, `serverless-wallet-app`).*
