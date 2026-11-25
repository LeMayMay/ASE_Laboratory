package com.instakill.funnyfy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instakill.common.error.BadRequestException;
import com.instakill.common.error.LlmServiceException;
import com.instakill.funnyfy.LlmProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunnyfyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void forwardsTextToConfiguredChatCompletionsEndpointAndReturnsFunnierText() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "model": "google/gemma-3-1b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "\\"My brain just achieved caffeinated confusion. ☕😂\\""
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        LlmProperties props = new LlmProperties(
                "http://localhost:12345/v1/chat/completions",
                "google/gemma-3-1b",
                15
        );
        FunnyfyService service = new FunnyfyService(props, objectMapper, httpClient);

        String result = service.funnyfy("Just had my morning coffee.");

        assertThat(result).isEqualTo("My brain just achieved caffeinated confusion. ☕😂");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.uri()).isEqualTo(URI.create("http://localhost:12345/v1/chat/completions"));
        assertThat(sentRequest.method()).isEqualTo("POST");
        assertThat(sentRequest.version()).contains(HttpClient.Version.HTTP_1_1);
        assertThat(sentRequest.headers().firstValue("Content-Type")).contains("application/json");

        String bodyString = readRequestBody(sentRequest);
        JsonNode jsonBody = objectMapper.readTree(bodyString);
        assertThat(jsonBody.get("model").asText()).isEqualTo("google/gemma-3-1b");
        assertThat(jsonBody.get("messages").isArray()).isTrue();
        assertThat(jsonBody.get("messages")).hasSize(2);
        assertThat(jsonBody.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(jsonBody.get("messages").get(1).get("role").asText()).isEqualTo("user");
        assertThat(jsonBody.get("messages").get(1).get("content").asText()).isEqualTo("Just had my morning coffee.");
    }

    @Test
    void appendsChatCompletionsPathWhenBaseUrlProvidedWithoutPath() {
        LlmProperties props = new LlmProperties("http://127.0.0.1:12345", "google/gemma-3-1b", 10);
        assertThat(props.resolvedEndpointUri())
                .isEqualTo(URI.create("http://127.0.0.1:12345/v1/chat/completions"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesTimeoutGracefully() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        LlmProperties props = new LlmProperties("http://localhost:12345/v1/chat/completions", "google/gemma-3-1b", 5);
        FunnyfyService service = new FunnyfyService(props, objectMapper, httpClient);

        assertThatThrownBy(() -> service.funnyfy("Waiting for my code to compile"))
                .isInstanceOf(LlmServiceException.class)
                .satisfies(ex -> {
                    LlmServiceException llmEx = (LlmServiceException) ex;
                    assertThat(llmEx.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(llmEx.getMessage()).containsIgnoringCase("timed out");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesServerNotRunningGracefully() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException("Connection refused"));

        LlmProperties props = new LlmProperties("http://localhost:12345/v1/chat/completions", "google/gemma-3-1b", 5);
        FunnyfyService service = new FunnyfyService(props, objectMapper, httpClient);

        assertThatThrownBy(() -> service.funnyfy("Hello world"))
                .isInstanceOf(LlmServiceException.class)
                .satisfies(ex -> {
                    LlmServiceException llmEx = (LlmServiceException) ex;
                    assertThat(llmEx.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(llmEx.getMessage()).containsIgnoringCase("LM Studio");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesNon2xxResponseFromLlmGracefully() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn("{\"error\":\"Model not loaded\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        LlmProperties props = new LlmProperties("http://localhost:12345/v1/chat/completions", "google/gemma-3-1b", 5);
        FunnyfyService service = new FunnyfyService(props, objectMapper, httpClient);

        assertThatThrownBy(() -> service.funnyfy("Hello world"))
                .isInstanceOf(LlmServiceException.class)
                .satisfies(ex -> {
                    LlmServiceException llmEx = (LlmServiceException) ex;
                    assertThat(llmEx.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(llmEx.getMessage()).contains("HTTP 404");
                });
    }

    @Test
    void rejectsBlankText() {
        HttpClient httpClient = mock(HttpClient.class);
        LlmProperties props = new LlmProperties("http://localhost:12345/v1/chat/completions", "google/gemma-3-1b", 5);
        FunnyfyService service = new FunnyfyService(props, objectMapper, httpClient);

        assertThatThrownBy(() -> service.funnyfy("   "))
                .isInstanceOf(BadRequestException.class);
    }

    private static String readRequestBody(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        List<ByteBuffer> buffers = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffers.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        int total = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
        byte[] bytes = new byte[total];
        int offset = 0;
        for (ByteBuffer buf : buffers) {
            int rem = buf.remaining();
            buf.get(bytes, offset, rem);
            offset += rem;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
