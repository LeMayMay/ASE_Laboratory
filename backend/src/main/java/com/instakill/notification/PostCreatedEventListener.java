package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous listener that bridges the {@link PostCreatedEvent} Spring application event
 * to the {@link NotificationService} port.
 *
 * <p>Using Spring's {@code @Async} + {@code @EventListener} means:
 * <ul>
 *   <li>The HTTP call never blocks the request thread that created the post.</li>
 *   <li>The event listener can be replaced by a proper message-queue consumer
 *       (e.g. RabbitMQ {@code @RabbitListener}) without changing any domain code.</li>
 * </ul>
 */
@Component
public class PostCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PostCreatedEventListener.class);

    private final NotificationService notificationService;

    public PostCreatedEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        log.debug("Handling PostCreatedEvent asynchronously for post {}", event.postId());
        notificationService.notifyPostCreated(event);
    }
}
