package com.example.wallet.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "wallet.aws.enabled", havingValue = "true")
public class AwsClientConfiguration {

    @Bean
    public DynamoDbClient dynamoDbClient(WalletAwsProperties props) {
        var builder = DynamoDbClient.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public EventBridgeClient eventBridgeClient(WalletAwsProperties props) {
        var builder = EventBridgeClient.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpointOverride()));
        }
        return builder.build();
    }
}
