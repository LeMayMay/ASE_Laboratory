package com.instakill.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution support.
 * All {@code @Async} methods run on a shared task executor.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
