package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link NotificationService} used when no active
 * notification adapter is configured. Follows the Null Object pattern already
 * established in the {@code interaction} bounded context.
 *
 * <p>Registered as a fallback bean by {@link NotificationConfig} via
 * {@code @ConditionalOnMissingBean}.
 */
public class NoOpNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NoOpNotificationService.class);

    @Override
    public void notifyPostCreated(PostCreatedEvent event) {
        log.debug("NoOp: notification suppressed for post {}", event.postId());
    }
}
