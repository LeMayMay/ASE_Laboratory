package com.instakill.post.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event raised whenever a new Post is successfully persisted.
 * Consumers should treat this as an immutable fact about a past occurrence.
 */
public record PostCreatedEvent(UUID postId, String username, Instant occurredAt) {
    public PostCreatedEvent {
        if (postId == null || username == null || occurredAt == null) {
            throw new IllegalArgumentException("postId, username, and occurredAt must not be null");
        }
    }
}
