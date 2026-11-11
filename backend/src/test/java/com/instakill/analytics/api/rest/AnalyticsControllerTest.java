package com.instakill.analytics.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsAllFiveSqlAnalyticsMetricsForLoggedInUser() throws Exception {
        Cookie authorCookie = registerOrLogin("analytics_author", "analytics_author@example.com", "password123");
        Cookie fan1Cookie = registerOrLogin("analytics_fan1", "analytics_fan1@example.com", "password123");
        Cookie fan2Cookie = registerOrLogin("analytics_fan2", "analytics_fan2@example.com", "password123");

        // Create 6 posts for author (1 photo, 1 video, 4 text) to test Top 5 limit and media type comparison
        UUID photoPostId = createPost(authorCookie, "Photo post", "/media/sample-photo.jpg");
        UUID videoPostId = createPost(authorCookie, "Video post", "/media/sample-video.mp4");
        UUID post3Id = createPost(authorCookie, "Post three", null);
        createPost(authorCookie, "Post four", null);
        createPost(authorCookie, "Post five", null);
        createPost(authorCookie, "Post six", null);

        // Photo post: 2 likes, 1 comment -> engagement = 3
        toggleLike(fan1Cookie, photoPostId);
        toggleLike(fan2Cookie, photoPostId);
        addComment(fan1Cookie, photoPostId, "Great photo!");

        // Video post: 1 like, 1 comment -> engagement = 2
        toggleLike(fan1Cookie, videoPostId);
        addComment(fan2Cookie, videoPostId, "Nice video!");

        // Post three: 1 like, 0 comments -> engagement = 1
        toggleLike(fan1Cookie, post3Id);

        // Fetch analytics via REST
        MvcResult result = mockMvc.perform(get("/api/analytics").cookie(authorCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        // 1. Weekly activity (grouped by week of creation)
        JsonNode weekly = json.get("weeklyActivity");
        assertThat(weekly.isArray()).isTrue();
        assertThat(weekly).isNotEmpty();
        assertThat(weekly.get(0).get("totalPosts").asLong()).isGreaterThanOrEqualTo(6L);
        assertThat(weekly.get(0).get("totalLikes").asLong()).isEqualTo(4L);
        assertThat(weekly.get(0).get("totalComments").asLong()).isEqualTo(2L);

        // 2. Top 5 posts by engagement
        JsonNode topPosts = json.get("topPostsByEngagement");
        assertThat(topPosts.isArray()).isTrue();
        assertThat(topPosts).hasSize(5); // capped at top 5 even though 6 posts exist
        assertThat(topPosts.get(0).get("postId").asText()).isEqualTo(photoPostId.toString());
        assertThat(topPosts.get(0).get("engagement").asLong()).isEqualTo(3L);
        assertThat(topPosts.get(1).get("postId").asText()).isEqualTo(videoPostId.toString());
        assertThat(topPosts.get(1).get("engagement").asLong()).isEqualTo(2L);

        // 3. Like-to-comment ratio (4 likes / 2 comments = 2.0)
        JsonNode ratio = json.get("likeCommentRatio");
        assertThat(ratio.get("totalLikes").asLong()).isEqualTo(4L);
        assertThat(ratio.get("totalComments").asLong()).isEqualTo(2L);
        assertThat(ratio.get("likeToCommentRatio").asDouble()).isEqualTo(2.0);

        // 4. Average likes per post by media type (PHOTO = 2.0, VIDEO = 1.0)
        JsonNode mediaTypes = json.get("averageLikesByMediaType");
        assertThat(mediaTypes.isArray()).isTrue();
        assertThat(mediaTypes).hasSize(2);
        for (JsonNode mt : mediaTypes) {
            String type = mt.get("mediaType").asText();
            if ("PHOTO".equals(type)) {
                assertThat(mt.get("postCount").asLong()).isEqualTo(1L);
                assertThat(mt.get("averageLikes").asDouble()).isEqualTo(2.0);
            } else if ("VIDEO".equals(type)) {
                assertThat(mt.get("postCount").asLong()).isEqualTo(1L);
                assertThat(mt.get("averageLikes").asDouble()).isEqualTo(1.0);
            }
        }

        // 5. Rank in top X% of users by average engagement
        JsonNode rank = json.get("engagementRank");
        assertThat(rank.get("rank").asLong()).isEqualTo(1L);
        assertThat(rank.get("totalUsers").asLong()).isGreaterThanOrEqualTo(3L);
        assertThat(rank.get("topPercentage").asDouble()).isGreaterThan(0.0).isLessThanOrEqualTo(100.0);

        // Verify MVC analytics view renders as well
        mockMvc.perform(get("/analytics").cookie(authorCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Analytics for @")));
    }

    @Test
    void unauthenticatedAnalyticsRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isUnauthorized());
    }

    private Cookie registerOrLogin(String username, String email, String password) throws Exception {
        String registerPayload = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password
        );
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("ACCESS_TOKEN");
        if (cookie == null) {
            String loginPayload = String.format(
                    "{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}",
                    username, password
            );
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();
            cookie = loginResult.getResponse().getCookie("ACCESS_TOKEN");
        }
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private UUID createPost(Cookie cookie, String content, String imageUrl) throws Exception {
        String imgJson = imageUrl == null ? "null" : "\"" + imageUrl + "\"";
        String payload = String.format("{\"content\":\"%s\",\"imageUrl\":%s}", content, imgJson);
        MvcResult res = mockMvc.perform(post("/api/posts")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private void toggleLike(Cookie cookie, UUID postId) throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/likes/toggle").cookie(cookie))
                .andExpect(status().isOk());
    }

    private void addComment(Cookie cookie, UUID postId, String content) throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }
}
