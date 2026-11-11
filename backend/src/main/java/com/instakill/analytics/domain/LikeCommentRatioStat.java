package com.instakill.analytics.domain;

/**
 * Combined like-to-comment ratio across all posts authored by the user.
 */
public record LikeCommentRatioStat(
        long totalLikes,
        long totalComments,
        double likeToCommentRatio
) {
}
