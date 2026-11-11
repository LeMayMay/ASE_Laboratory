package com.instakill.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the notification subsystem.
 * Binds from the {@code notification.ntfy} prefix.
 */
@ConfigurationProperties(prefix = "notification.ntfy")
public record NotificationProperties(
        boolean enabled,
        String baseUrl,
        String topic
) {
    public NotificationProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://ntfy.sh";
        if (topic == null || topic.isBlank()) topic = "instakill-notifications";
    }
}
