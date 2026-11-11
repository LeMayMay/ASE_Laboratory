package com.instakill.analytics.domain;

import java.util.Objects;

/**
 * Aggregated weekly counts of posts, likes, and comments for a user,
 * grouped by week of post creation (e.g. "2026-W45").
 */
public record WeeklyActivityStat(
        String week,
        long totalPosts,
        long totalLikes,
        long totalComments
) {
    public WeeklyActivityStat {
        Objects.requireNonNull(week, "week must not be null");
    }
}
