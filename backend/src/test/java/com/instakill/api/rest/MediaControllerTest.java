package com.instakill.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie authToken;

    @BeforeEach
    void setUp() throws Exception {
        // Register or login a user to get an auth cookie
        String payload = "{\"username\":\"mediauser\",\"email\":\"mediauser@example.com\",\"password\":\"password123\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        authToken = result.getResponse().getCookie("ACCESS_TOKEN");
        if (authToken == null) {
            // Already registered, try login
            String loginPayload = "{\"usernameOrEmail\":\"mediauser\",\"password\":\"password123\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();
            authToken = loginResult.getResponse().getCookie("ACCESS_TOKEN");
        }
        assertThat(authToken).isNotNull();
    }

    @Test
    void uploadImageSuccessAndServe() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imageBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authToken))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();
        assertThat(mediaUrl).startsWith("/media/").endsWith(".jpg");

        // Verify static serving via WebMvcConfigurer
        MvcResult serveResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(serveResult.getResponse().getContentAsByteArray()).isEqualTo(imageBytes);
    }

    @Test
    void uploadVideoSuccessAndServe() throws Exception {
        byte[] videoBytes = new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}; // mock MP4 header
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-clip.mp4",
                "video/mp4",
                videoBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .cookie(authToken))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();
        assertThat(mediaUrl).startsWith("/media/").endsWith(".mp4");

        // Verify static serving
        MvcResult serveResult = mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(serveResult.getResponse().getContentAsByteArray()).isEqualTo(videoBytes);
    }

    @Test
    void uploadEmptyFileFails() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/media")
                        .file(emptyFile)
                        .cookie(authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadDisallowedExtensionFails() throws Exception {
        MockMultipartFile scriptFile = new MockMultipartFile(
                "file",
                "virus.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/media")
                        .file(scriptFile)
                        .cookie(authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedUploadRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/media")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }
}
