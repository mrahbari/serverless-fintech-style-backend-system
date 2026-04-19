package com.example.wallet.event;

public interface EventPublisher {

    void publish(DomainEvent event);
}
