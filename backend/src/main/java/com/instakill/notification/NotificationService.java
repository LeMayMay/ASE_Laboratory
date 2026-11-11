package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;

/**
 * Port interface for outbound notifications.
 * Implementations may target ntfy.sh, Slack, email, or a message broker.
 * The interface is intentionally narrow: callers should not know the transport.
 */
public interface NotificationService {
    void notifyPostCreated(PostCreatedEvent event);
}
