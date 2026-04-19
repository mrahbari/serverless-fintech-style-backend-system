package com.example.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When {@code enabled=false} (default), in-memory implementations run so tests and local dev work without AWS.
 * Set {@code enabled=true} plus table names and credentials (or IAM role) for real DynamoDB + EventBridge.
 */
@ConfigurationProperties(prefix = "wallet.aws")
public class WalletAwsProperties {

    /**
     * Use real AWS SDK clients and DynamoDB/EventBridge.
     */
    private boolean enabled = false;

    private String region = "eu-west-1";

    /**
     * Optional base endpoint (e.g. http://localhost:4566 for LocalStack).
     */
    private String endpointOverride;

    private String accountsTable = "wallet-accounts";

    private String idempotencyTable = "wallet-transfer-idempotency";

    /**
     * Default event bus name in the account (often {@code default}).
     */
    private String eventBusName = "default";

    private String eventSource = "wallet.wallet";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public String getAccountsTable() {
        return accountsTable;
    }

    public void setAccountsTable(String accountsTable) {
        this.accountsTable = accountsTable;
    }

    public String getIdempotencyTable() {
        return idempotencyTable;
    }

    public void setIdempotencyTable(String idempotencyTable) {
        this.idempotencyTable = idempotencyTable;
    }

    public String getEventBusName() {
        return eventBusName;
    }

    public void setEventBusName(String eventBusName) {
        this.eventBusName = eventBusName;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }
}
