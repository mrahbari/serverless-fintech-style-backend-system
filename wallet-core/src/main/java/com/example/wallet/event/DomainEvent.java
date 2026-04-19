package com.example.wallet.event;

import java.time.Instant;

/**
 * EventBridge rule targets would filter on {@code type()} in a real AWS deployment.
 */
public interface DomainEvent {

    String type();

    Instant occurredAt();
}
