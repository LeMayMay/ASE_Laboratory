package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void ntfyNotificationServiceFormatsAndSendsHttpPost() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        NotificationProperties props = new NotificationProperties(true, "https://ntfy.sh", "instakill-test-topic");
        NtfyNotificationService service = new NtfyNotificationService(props, httpClient);

        UUID postId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Instant timestamp = Instant.parse("2026-11-11T10:30:00Z");
        PostCreatedEvent event = new PostCreatedEvent(postId, "alice", timestamp);

        service.notifyPostCreated(event);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.uri()).isEqualTo(URI.create("https://ntfy.sh/instakill-test-topic"));
        assertThat(sentRequest.method()).isEqualTo("POST");
        assertThat(sentRequest.headers().firstValue("Title")).contains("New Instakill Post");

        String formatted = service.formatMessage(event);
        assertThat(formatted)
                .contains("@alice")
                .contains(postId.toString())
                .contains("2026-11-11T10:30:00Z");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ntfyNotificationServiceSwallowsNetworkExceptions() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Simulated network error"));

        NotificationProperties props = new NotificationProperties(true, "https://ntfy.sh", "instakill-test-topic");
        NtfyNotificationService service = new NtfyNotificationService(props, httpClient);

        PostCreatedEvent event = new PostCreatedEvent(UUID.randomUUID(), "bob", Instant.now());

        assertThatCode(() -> service.notifyPostCreated(event)).doesNotThrowAnyException();
    }

    @Test
    void postCreatedEventListenerDelegatesToNotificationService() {
        NotificationService notificationService = mock(NotificationService.class);
        PostCreatedEventListener listener = new PostCreatedEventListener(notificationService);
        PostCreatedEvent event = new PostCreatedEvent(UUID.randomUUID(), "alice", Instant.now());

        listener.onPostCreated(event);

        verify(notificationService).notifyPostCreated(event);
    }
}
