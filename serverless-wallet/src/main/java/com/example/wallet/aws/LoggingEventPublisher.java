package com.example.wallet.aws;

import com.example.wallet.event.DomainEvent;
import com.example.wallet.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when {@code wallet.aws.enabled=false}: logs events instead of calling EventBridge.
 */
@Component
@ConditionalOnProperty(name = "wallet.aws.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(DomainEvent event) {
        log.info("event_local_publish type={} payload={}", event.type(), event);
    }
}
