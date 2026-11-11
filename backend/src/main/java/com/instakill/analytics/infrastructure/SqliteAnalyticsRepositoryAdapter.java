package com.instakill.analytics.infrastructure;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.LikeCommentRatioStat;
import com.instakill.analytics.domain.MediaTypeLikesStat;
import com.instakill.analytics.domain.TopPostStat;
import com.instakill.analytics.domain.UserEngagementRankStat;
import com.instakill.analytics.domain.WeeklyActivityStat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SQLite SQL adapter for {@link AnalyticsRepository}.
 * Executes native SQL queries directly against SQLite for high-performance aggregation.
 */
@Repository
public class SqliteAnalyticsRepositoryAdapter implements AnalyticsRepository {

    private static final String WEEKLY_ACTIVITY_SQL = """
            SELECT
                CASE
                    WHEN typeof(p.created_at) IN ('integer', 'real')
                        THEN strftime('%Y-W%W', p.created_at / 1000, 'unixepoch')
                    ELSE strftime('%Y-W%W', p.created_at)
                END AS week,
                COUNT(*) AS total_posts,
                COALESCE(SUM(p.like_count), 0) AS total_likes,
                COALESCE(SUM(p.comment_count), 0) AS total_comments
            FROM posts p
            WHERE p.author_id = ?
            GROUP BY week
            ORDER BY week DESC
            """;

    private static final String TOP_POSTS_BY_ENGAGEMENT_SQL = """
            SELECT
                p.id,
                p.content,
                p.image_url,
                p.like_count,
                p.comment_count,
                (p.like_count + p.comment_count) AS engagement,
                p.created_at
            FROM posts p
            WHERE p.author_id = ?
            ORDER BY engagement DESC, p.like_count DESC, p.created_at DESC
            LIMIT ?
            """;

    private static final String LIKE_COMMENT_RATIO_SQL = """
            SELECT
                COALESCE(SUM(p.like_count), 0) AS total_likes,
                COALESCE(SUM(p.comment_count), 0) AS total_comments,
                CASE
                    WHEN COALESCE(SUM(p.comment_count), 0) = 0
                        THEN CAST(COALESCE(SUM(p.like_count), 0) AS REAL)
                    ELSE CAST(SUM(p.like_count) AS REAL) / CAST(SUM(p.comment_count) AS REAL)
                END AS like_to_comment_ratio
            FROM posts p
            WHERE p.author_id = ?
            """;

    private static final String AVERAGE_LIKES_BY_MEDIA_TYPE_SQL = """
            WITH media_types(media_type) AS (
                VALUES ('PHOTO'), ('VIDEO')
            ),
            classified_posts AS (
                SELECT
                    CASE
                        WHEN LOWER(p.image_url) LIKE '%.mp4%'
                          OR LOWER(p.image_url) LIKE '%.mov%'
                          OR LOWER(p.image_url) LIKE '%.webm%'
                          OR LOWER(p.image_url) LIKE '%.mkv%'
                            THEN 'VIDEO'
                        ELSE 'PHOTO'
                    END AS media_type,
                    p.like_count
                FROM posts p
                WHERE p.author_id = ?
                  AND p.image_url IS NOT NULL
                  AND TRIM(p.image_url) <> ''
            )
            SELECT
                mt.media_type,
                COUNT(cp.like_count) AS post_count,
                COALESCE(SUM(cp.like_count), 0) AS total_likes,
                COALESCE(AVG(CAST(cp.like_count AS REAL)), 0.0) AS avg_likes
            FROM media_types mt
            LEFT JOIN classified_posts cp ON cp.media_type = mt.media_type
            GROUP BY mt.media_type
            ORDER BY mt.media_type ASC
            """;

    private static final String USER_ENGAGEMENT_RANK_SQL = """
            WITH user_engagement AS (
                SELECT
                    u.id AS user_id,
                    COALESCE(AVG(CAST(p.like_count + p.comment_count AS REAL)), 0.0) AS avg_engagement
                FROM users u
                LEFT JOIN posts p ON p.author_id = u.id
                GROUP BY u.id
            ),
            ranked_users AS (
                SELECT
                    user_id,
                    avg_engagement,
                    RANK() OVER (ORDER BY avg_engagement DESC) AS user_rank,
                    COUNT(*) OVER () AS total_users
                FROM user_engagement
            )
            SELECT
                avg_engagement,
                user_rank,
                total_users,
                CASE
                    WHEN total_users > 0 THEN (CAST(user_rank AS REAL) * 100.0) / CAST(total_users AS REAL)
                    ELSE 100.0
                END AS top_percentage
            FROM ranked_users
            WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SqliteAnalyticsRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<WeeklyActivityStat> findWeeklyActivityByAuthor(UUID authorId) {
        return jdbcTemplate.query(
                WEEKLY_ACTIVITY_SQL,
                (rs, rowNum) -> new WeeklyActivityStat(
                        rs.getString("week") != null ? rs.getString("week") : "Unknown",
                        rs.getLong("total_posts"),
                        rs.getLong("total_likes"),
                        rs.getLong("total_comments")
                ),
                authorId.toString()
        );
    }

    @Override
    public List<TopPostStat> findTopPostsByEngagement(UUID authorId, int limit) {
        return jdbcTemplate.query(
                TOP_POSTS_BY_ENGAGEMENT_SQL,
                (rs, rowNum) -> new TopPostStat(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("content"),
                        rs.getString("image_url"),
                        rs.getLong("like_count"),
                        rs.getLong("comment_count"),
                        rs.getLong("engagement"),
                        parseInstant(rs.getObject("created_at"))
                ),
                authorId.toString(),
                limit
        );
    }

    @Override
    public LikeCommentRatioStat findLikeCommentRatioByAuthor(UUID authorId) {
        List<LikeCommentRatioStat> results = jdbcTemplate.query(
                LIKE_COMMENT_RATIO_SQL,
                (rs, rowNum) -> new LikeCommentRatioStat(
                        rs.getLong("total_likes"),
                        rs.getLong("total_comments"),
                        rs.getDouble("like_to_comment_ratio")
                ),
                authorId.toString()
        );
        return results.isEmpty() ? new LikeCommentRatioStat(0L, 0L, 0.0) : results.getFirst();
    }

    @Override
    public List<MediaTypeLikesStat> findAverageLikesByMediaType(UUID authorId) {
        return jdbcTemplate.query(
                AVERAGE_LIKES_BY_MEDIA_TYPE_SQL,
                (rs, rowNum) -> new MediaTypeLikesStat(
                        rs.getString("media_type"),
                        rs.getLong("post_count"),
                        rs.getLong("total_likes"),
                        rs.getDouble("avg_likes")
                ),
                authorId.toString()
        );
    }

    @Override
    public UserEngagementRankStat findEngagementRankForUser(UUID userId) {
        List<UserEngagementRankStat> results = jdbcTemplate.query(
                USER_ENGAGEMENT_RANK_SQL,
                (rs, rowNum) -> new UserEngagementRankStat(
                        rs.getDouble("avg_engagement"),
                        rs.getLong("user_rank"),
                        rs.getLong("total_users"),
                        rs.getDouble("top_percentage")
                ),
                userId.toString()
        );
        return results.isEmpty() ? new UserEngagementRankStat(0.0, 1L, 1L, 100.0) : results.getFirst();
    }

    private Instant parseInstant(Object rawValue) {
        if (rawValue == null) {
            return Instant.EPOCH;
        }
        if (rawValue instanceof Number num) {
            return Instant.ofEpochMilli(num.longValue());
        }
        if (rawValue instanceof Timestamp ts) {
            return ts.toInstant();
        }
        String text = rawValue.toString().trim();
        try {
            return Instant.ofEpochMilli(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return Instant.parse(text);
        }
    }
}
