package com.instakill.analytics.domain;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for SQL-backed user analytics queries.
 */
public interface AnalyticsRepository {

    List<WeeklyActivityStat> findWeeklyActivityByAuthor(UUID authorId);

    List<TopPostStat> findTopPostsByEngagement(UUID authorId, int limit);

    LikeCommentRatioStat findLikeCommentRatioByAuthor(UUID authorId);

    List<MediaTypeLikesStat> findAverageLikesByMediaType(UUID authorId);

    UserEngagementRankStat findEngagementRankForUser(UUID userId);
}
