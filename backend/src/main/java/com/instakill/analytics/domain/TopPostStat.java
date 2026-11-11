package com.instakill.analytics.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Summary of a post ranked by total engagement (likes + comments).
 */
public record TopPostStat(
        UUID postId,
        String content,
        String imageUrl,
        long likeCount,
        long commentCount,
        long engagement,
        Instant createdAt
) {
    public TopPostStat {
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
