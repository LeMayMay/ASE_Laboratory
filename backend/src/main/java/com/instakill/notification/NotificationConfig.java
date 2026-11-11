package com.instakill.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that registers the appropriate {@link NotificationService} implementation.
 *
 * <p>Uses {@code @ConditionalOnProperty} / {@code @ConditionalOnMissingBean} inside a
 * {@code @Configuration} class so that Spring Boot's condition evaluation order is well-defined
 * (conditions on {@code @Bean} methods in {@code @Configuration} classes are always evaluated
 * correctly, unlike {@code @ConditionalOnMissingBean} on {@code @Component} classes).
 */
@Configuration
public class NotificationConfig {

    /**
     * Registers the ntfy.sh adapter when {@code notification.ntfy.enabled=true} (default if missing).
     */
    @Bean
    @ConditionalOnProperty(prefix = "notification.ntfy", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NotificationService ntfyNotificationService(NotificationProperties properties) {
        return new NtfyNotificationService(properties);
    }

    /**
     * Registers the no-op fallback when no active notification adapter is present.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService noOpNotificationService() {
        return new NoOpNotificationService();
    }
}
