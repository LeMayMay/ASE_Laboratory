package com.instakill.funnyfy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Externalised configuration for the local LLM (LM Studio) integration.
 * Binds from the {@code llm} prefix (backed by {@code LOCAL_LLM_URL}).
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String url,
        String model,
        int timeoutSeconds
) {
    public static final String DEFAULT_URL = "http://localhost:12345/v1/chat/completions";
    public static final String DEFAULT_MODEL = "google/gemma-3-270m-it";
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    public LlmProperties {
        if (url == null || url.isBlank()) {
            String envUrl = System.getenv("LOCAL_LLM_URL");
            url = (envUrl != null && !envUrl.isBlank()) ? envUrl : DEFAULT_URL;
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public URI resolvedEndpointUri() {
        String trimmed = url.trim();
        URI parsed = URI.create(trimmed);
        String path = parsed.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            String base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            return URI.create(base + "/v1/chat/completions");
        }
        return parsed;
    }
}
