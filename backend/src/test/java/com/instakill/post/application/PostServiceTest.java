package com.instakill.post.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.id.IdGenerator;
import com.instakill.feed.application.FeedService;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.post.domain.PostRepository;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private final PostRepository postRepository = mock(PostRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);
    private final FeedService feedService = mock(FeedService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, idGenerator, clock, feedService, eventPublisher);
    }

    @Test
    void createPostSavesAndPublishesPostCreatedEvent() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        User alice = new User(authorId, "alice", "alice@example.com", "hash", now);

        when(idGenerator.generate()).thenReturn(postId);
        when(clock.now()).thenReturn(now);
        when(userRepository.findById(authorId)).thenReturn(Optional.of(alice));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post created = postService.createPost(authorId, "Hello notifications!", "/media/sample.jpg");

        assertThat(created.id()).isEqualTo(postId);
        assertThat(created.authorId()).isEqualTo(authorId);
        assertThat(created.content()).isEqualTo("Hello notifications!");

        ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        PostCreatedEvent event = eventCaptor.getValue();
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.occurredAt()).isEqualTo(now);
    }
}
