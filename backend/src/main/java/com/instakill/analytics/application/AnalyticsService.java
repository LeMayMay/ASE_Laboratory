package com.instakill.analytics.application;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.UserAnalytics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service orchestrating user-level SQL analytics queries.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int TOP_POSTS_LIMIT = 5;

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public UserAnalytics getAnalyticsForUser(UUID userId) {
        return new UserAnalytics(
                analyticsRepository.findWeeklyActivityByAuthor(userId),
                analyticsRepository.findTopPostsByEngagement(userId, TOP_POSTS_LIMIT),
                analyticsRepository.findLikeCommentRatioByAuthor(userId),
                analyticsRepository.findAverageLikesByMediaType(userId),
                analyticsRepository.findEngagementRankForUser(userId)
        );
    }
}
