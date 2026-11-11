package com.instakill.post.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.error.ForbiddenException;
import com.instakill.common.error.NotFoundException;
import com.instakill.common.id.IdGenerator;
import com.instakill.feed.application.FeedService;
import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.post.domain.PostRepository;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository posts;
    private final UserRepository users;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final FeedService feedService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PostService(PostRepository posts,
                       UserRepository users,
                       IdGenerator idGenerator,
                       Clock clock,
                       FeedService feedService,
                       ApplicationEventPublisher eventPublisher) {
        this.posts = posts;
        this.users = users;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.feedService = feedService;
        this.eventPublisher = eventPublisher;
    }

    public PostService(PostRepository posts, IdGenerator idGenerator, Clock clock, FeedService feedService) {
        this(posts, null, idGenerator, clock, feedService, null);
    }

    @Transactional
    public Post createPost(UUID authorId, String content, String imageUrl) {
        Instant now = clock.now();
        Post post = new Post(
                idGenerator.generate(),
                authorId,
                content,
                imageUrl,
                now,
                now,
                0L,
                0L
        );
        Post saved = posts.save(post);
        if (eventPublisher != null) {
            String username = (users != null)
                    ? users.findById(authorId).map(User::username).orElse("unknown")
                    : "unknown";
            eventPublisher.publishEvent(new PostCreatedEvent(saved.id(), username, saved.createdAt()));
        }
        return saved;
    }

    public Post getPost(UUID id) {
        return posts.findById(id).orElseThrow(() -> new NotFoundException("Post not found"));
    }

    @Transactional
    public void deleteOwnPost(UUID postId, UUID requesterId) {
        Post post = getPost(postId);
        if (!post.authorId().equals(requesterId)) {
            throw new ForbiddenException("Cannot delete another user's post");
        }
        posts.deleteById(postId);
    }

    public List<Post> listFeed(FeedSort sort, int page, int size) {
        return feedService.listFeed(sort, page, size);
    }
}
