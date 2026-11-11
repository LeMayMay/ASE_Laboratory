package com.instakill.analytics.domain;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate analytics view for the currently logged-in user.
 */
public record UserAnalytics(
        List<WeeklyActivityStat> weeklyActivity,
        List<TopPostStat> topPostsByEngagement,
        LikeCommentRatioStat likeCommentRatio,
        List<MediaTypeLikesStat> averageLikesByMediaType,
        UserEngagementRankStat engagementRank
) {
    public UserAnalytics {
        Objects.requireNonNull(weeklyActivity, "weeklyActivity must not be null");
        Objects.requireNonNull(topPostsByEngagement, "topPostsByEngagement must not be null");
        Objects.requireNonNull(likeCommentRatio, "likeCommentRatio must not be null");
        Objects.requireNonNull(averageLikesByMediaType, "averageLikesByMediaType must not be null");
        Objects.requireNonNull(engagementRank, "engagementRank must not be null");
    }
}
