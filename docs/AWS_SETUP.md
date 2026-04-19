# AWS setup for `wallet.aws.enabled=true`

Overview: [../README.md](../README.md) · Implementation map: [IMPLEMENTATION-DETAILS.md](IMPLEMENTATION-DETAILS.md) · Persian: [README-FA.md](README-FA.md).

Set `wallet.aws.enabled=true` or environment variable `WALLET_AWS_ENABLED=true` (see `application.yml`) and provide credentials via the default AWS SDK chain (environment variables, `~/.aws/credentials`, or IAM role).

## DynamoDB tables

**Accounts** (name defaults to `wallet-accounts`, overridable with `WALLET_ACCOUNTS_TABLE`):

```bash
aws dynamodb create-table \
  --table-name wallet-accounts \
  --attribute-definitions AttributeName=accountId,AttributeType=S \
  --key-schema AttributeName=accountId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

**Transfer idempotency** (defaults to `wallet-transfer-idempotency`, overridable with `WALLET_IDEMPOTENCY_TABLE`):

```bash
aws dynamodb create-table \
  --table-name wallet-transfer-idempotency \
  --attribute-definitions AttributeName=transactionId,AttributeType=S \
  --key-schema AttributeName=transactionId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

## EventBridge

The app calls `PutEvents` on the **default** event bus unless you set `wallet.aws.event-bus-name` / `WALLET_EVENT_BUS_NAME`.

Create EventBridge rules that match `source` = `wallet.wallet` (configurable via `wallet.aws.event-source`) and `detail-type` = `ACCOUNT_CREATED`, `DEPOSIT_COMPLETED`, or `TRANSFER_COMPLETED` to invoke Lambda targets (notifications, analytics).

## LocalStack

Point the SDK at LocalStack:

```yaml
wallet:
  aws:
    enabled: true
    endpoint-override: http://localhost:4566
    region: eu-west-1
```

Use `AWS_ACCESS_KEY_ID=test` and `AWS_SECRET_ACCESS_KEY=test` as required by LocalStack.
