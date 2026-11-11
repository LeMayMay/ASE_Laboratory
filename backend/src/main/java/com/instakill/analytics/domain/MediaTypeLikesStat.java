package com.instakill.analytics.domain;

import java.util.Objects;

/**
 * Average likes per post segmented by media type ("PHOTO" vs "VIDEO").
 */
public record MediaTypeLikesStat(
        String mediaType,
        long postCount,
        long totalLikes,
        double averageLikes
) {
    public MediaTypeLikesStat {
        Objects.requireNonNull(mediaType, "mediaType must not be null");
    }
}
