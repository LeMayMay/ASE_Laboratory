package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends post-creation notifications to ntfy.sh via HTTP.
 * Registered as a bean when {@code notification.ntfy.enabled=true} (see {@link NotificationConfig}).
 *
 * <p>This adapter can be replaced by any other {@link NotificationService} implementation
 * (e.g., Kafka producer, RabbitMQ publisher, Slack webhook) without changing the domain or
 * application layer.
 */
public class NtfyNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationService.class);

    private final NotificationProperties properties;
    private final HttpClient httpClient;

    public NtfyNotificationService(NotificationProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public NtfyNotificationService(NotificationProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public void notifyPostCreated(PostCreatedEvent event) {
        String message = formatMessage(event);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/" + properties.topic()))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("Title", "New Instakill Post")
                    .header("Tags", "camera,sparkles")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("ntfy.sh notification sent for post {}: HTTP {}", event.postId(), response.statusCode());
        } catch (Exception e) {
            // Notification failures must never propagate to the caller — they are best-effort.
            log.warn("Failed to send ntfy.sh notification for post {}: {}", event.postId(), e.getMessage());
        }
    }

    String formatMessage(PostCreatedEvent event) {
        return String.format(
                "New post by @%s%n  Post ID : %s%n  Created : %s",
                event.username(),
                event.postId(),
                event.occurredAt()
        );
    }
}
