package com.instakill.interaction.domain;

import org.springframework.stereotype.Component;

@Component
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(Object event) {
        // Explicitly does nothing to fulfill the Null Object pattern
    }
}