package com.example.wallet.config;

import com.example.wallet.aws.EventBridgeEventPublisher;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.repository.AccountRepository;
import com.example.wallet.repository.DynamoDbAccountRepository;
import com.example.wallet.service.DynamoDbTransferExecutionService;
import com.example.wallet.service.TransferExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@Configuration
@ConditionalOnProperty(name = "wallet.aws.enabled", havingValue = "true")
public class WalletAwsServiceConfiguration {

    @Bean
    public AccountRepository accountRepository(DynamoDbClient dynamoDb, WalletAwsProperties props) {
        return new DynamoDbAccountRepository(dynamoDb, props.getAccountsTable());
    }

    @Bean
    public EventPublisher eventPublisher(
            EventBridgeClient eventBridge, WalletAwsProperties props, ObjectMapper objectMapper) {
        return new EventBridgeEventPublisher(
                eventBridge, props.getEventBusName(), props.getEventSource(), objectMapper);
    }

    @Bean
    public TransferExecutionService transferExecutionService(
            DynamoDbClient dynamoDb,
            WalletAwsProperties props,
            AccountRepository accountRepository,
            EventPublisher eventPublisher) {
        return new DynamoDbTransferExecutionService(
                dynamoDb,
                props.getAccountsTable(),
                props.getIdempotencyTable(),
                accountRepository,
                eventPublisher);
    }
}
