package com.instakill.funnyfy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.common.error.BadRequestException;
import com.instakill.common.error.LlmServiceException;
import com.instakill.funnyfy.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Application service that rewrites post captions into funnier versions
 * by calling an OpenAI-compatible {@code /v1/chat/completions} endpoint (e.g. LM Studio).
 */
@Service
public class FunnyfyService {

    private static final Logger log = LoggerFactory.getLogger(FunnyfyService.class);

    static final String SYSTEM_PROMPT =
            "You are a witty social media comedy writer. Rewrite the user's social media post caption " +
            "to make it much funnier, wittier, and more entertaining while keeping the original topic. " +
            "Return ONLY the single rewritten caption text, with no introduction, no multiple options, " +
            "and no surrounding quotes.";

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public FunnyfyService(LlmProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
        );
    }

    public FunnyfyService(LlmProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String funnyfy(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Text must not be blank");
        }

        URI endpoint = properties.resolvedEndpointUri();
        ChatCompletionRequest payload = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        new ChatMessage("system", SYSTEM_PROMPT),
                        new ChatMessage("user", text.trim())
                ),
                0.8
        );

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize chat completion request", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            log.warn("Timeout waiting for local LLM at {}: {}", endpoint, e.getMessage());
            throw LlmServiceException.timeout("Local LLM request timed out. Please try again.", e);
        } catch (ConnectException e) {
            log.warn("Connection refused by local LLM at {}: {}", endpoint, e.getMessage());
            throw LlmServiceException.unavailable(
                    "Local LLM server is unreachable. Please ensure LM Studio is running on the configured port.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while calling local LLM at {}", endpoint, e);
            throw LlmServiceException.unavailable("Local LLM request was interrupted.", e);
        } catch (IOException e) {
            log.warn("I/O error communicating with local LLM at {}: {}", endpoint, e.getMessage());
            throw LlmServiceException.unavailable(
                    "Could not communicate with local LLM server. Please verify LM Studio is running.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Local LLM at {} returned HTTP {}: {}", endpoint, response.statusCode(), response.body());
            throw LlmServiceException.badGateway(
                    "Local LLM returned an error (HTTP " + response.statusCode() + "). Ensure the model is loaded in LM Studio.");
        }

        return extractContent(response.body());
    }

    String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw LlmServiceException.badGateway("Local LLM returned an empty response body.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw LlmServiceException.badGateway("Local LLM response did not contain any completion choices.");
            }
            String rawContent = choices.get(0).path("message").path("content").asText("");
            String cleaned = cleanOutput(rawContent);
            if (cleaned.isEmpty()) {
                throw LlmServiceException.badGateway("Local LLM returned blank completion text.");
            }
            return cleaned;
        } catch (LlmServiceException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Failed to parse local LLM response JSON: {}", e.getMessage());
            throw LlmServiceException.badGateway("Failed to parse response from local LLM.");
        }
    }

    static String cleanOutput(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    record ChatMessage(String role, String content) {
    }

    record ChatCompletionRequest(String model, List<ChatMessage> messages, double temperature) {
    }
}
