package com.example.wallet.aws;

import com.example.wallet.event.AccountCreatedEvent;
import com.example.wallet.event.DepositCompletedEvent;
import com.example.wallet.event.DomainEvent;
import com.example.wallet.event.EventPublisher;
import com.example.wallet.event.TransferCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.util.Map;

/**
 * Publishes domain events to Amazon EventBridge ({@code PutEvents}). Downstream rules typically invoke
 * Lambda targets (notifications, metrics) — not in-process listeners.
 */
public class EventBridgeEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventBridgeEventPublisher.class);

    private final EventBridgeClient eventBridge;
    private final String eventBusName;
    private final String eventSource;
    private final ObjectMapper objectMapper;

    public EventBridgeEventPublisher(
            EventBridgeClient eventBridge,
            String eventBusName,
            String eventSource,
            ObjectMapper objectMapper) {
        this.eventBridge = eventBridge;
        this.eventBusName = eventBusName;
        this.eventSource = eventSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        String detail;
        try {
            detail = objectMapper.writeValueAsString(toDetailMap(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.type(), e);
        }
        var entry = PutEventsRequestEntry.builder()
                .eventBusName(eventBusName)
                .source(eventSource)
                .detailType(event.type())
                .detail(detail)
                .build();
        log.info("eventbridge_put type={} detail={}", event.type(), detail);
        var response = eventBridge.putEvents(PutEventsRequest.builder().entries(entry).build());
        if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
            log.error("eventbridge_put_failed entries={}", response.entries());
            throw new IllegalStateException("EventBridge PutEvents failed for " + event.type());
        }
    }

    private static Map<String, Object> toDetailMap(DomainEvent event) {
        if (event instanceof AccountCreatedEvent e) {
            return Map.of(
                    "accountId", e.accountId(),
                    "initialBalance", e.initialBalance(),
                    "occurredAt", e.occurredAt().toString());
        }
        if (event instanceof DepositCompletedEvent e) {
            return Map.of(
                    "accountId", e.accountId(),
                    "amount", e.amount(),
                    "newBalance", e.newBalance(),
                    "occurredAt", e.occurredAt().toString());
        }
        if (event instanceof TransferCompletedEvent e) {
            return Map.of(
                    "transactionId", e.transactionId(),
                    "fromAccountId", e.fromAccountId(),
                    "toAccountId", e.toAccountId(),
                    "amount", e.amount(),
                    "fromBalanceAfter", e.fromBalanceAfter(),
                    "toBalanceAfter", e.toBalanceAfter(),
                    "occurredAt", e.occurredAt().toString());
        }
        throw new IllegalArgumentException("Unknown event: " + event);
    }
}
