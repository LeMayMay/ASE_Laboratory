package com.instakill.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        String basePath,
        Set<String> allowedExtensions
) {
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm", "mkv"
    );

    public MediaProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "media";
        }
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            allowedExtensions = DEFAULT_EXTENSIONS;
        }
    }
}
