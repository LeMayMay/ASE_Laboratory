package com.instakill.analytics.domain;

/**
 * Percentile ranking of the user among all users by average post engagement (likes + comments per post).
 */
public record UserEngagementRankStat(
        double averageEngagement,
        long rank,
        long totalUsers,
        double topPercentage
) {
}
