package com.smartlab.service.impl;

import com.smartlab.dto.request.PostCommentRequest;
import com.smartlab.dto.response.CursorPageResponse;
import com.smartlab.dto.response.PostAuthorResponse;
import com.smartlab.dto.response.PostCategoryResponse;
import com.smartlab.dto.response.PostCommentResponse;
import com.smartlab.dto.response.PostFeedResponse;
import com.smartlab.dto.response.PostReactionResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostCommentEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReactionEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostReactionType;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostCommentRepository;
import com.smartlab.repo.PostReactionRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostSocialServiceImpl implements PostSocialService {
    private static final int MAX_LIMIT = 50;
    private static final CursorKey FIRST_PAGE = new CursorKey(
            Instant.parse("9999-12-31T23:59:59.999Z"), Long.MAX_VALUE
    );

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ContentCategoryRepository contentCategoryRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostCommentRepository postCommentRepository;

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostFeedResponse> getFeed(String authenticatedEmail, String cursor, int requestedLimit) {
        UserEntity viewer = authenticatedEmail == null ? null : resolveActiveUser(authenticatedEmail);
        int limit = validatedLimit(requestedLimit);
        CursorKey cursorKey = cursor == null || cursor.isBlank() ? FIRST_PAGE : decodeCursor(cursor);
        List<PostEntity> fetched = viewer == null
                ? postRepository.findActivePublicPublishedFeedPage(
                        cursorKey.instant(), cursorKey.id(), PageRequest.of(0, limit + 1)
                )
                : postRepository.findActivePublishedFeedPage(
                        activeProjectIdsOrNoMatch(viewer.getId()),
                        cursorKey.instant(),
                        cursorKey.id(),
                        PageRequest.of(0, limit + 1)
                );
        boolean hasMore = fetched.size() > limit;
        List<PostEntity> posts = hasMore ? fetched.subList(0, limit) : fetched;
        List<Long> postIds = posts.stream().map(PostEntity::getId).toList();
        Map<Long, PostCategoryResponse> categories = findCategories(posts);
        Map<Long, PostAuthorResponse> authors = findAuthorsForPosts(posts);
        Map<Long, PostReactionType> viewerReactions = viewer == null
                ? Map.of()
                : findViewerReactions(postIds, viewer.getId());
        Map<Long, Map<PostReactionType, Long>> reactionCounts = findReactionCounts(postIds);
        Map<Long, Long> commentCounts = findCommentCounts(postIds);

        List<PostFeedResponse> items = posts.stream().map(post -> {
            Map<PostReactionType, Long> counts = reactionCounts.getOrDefault(post.getId(), emptyReactionCounts());
            return PostFeedResponse.builder()
                    .id(post.getId())
                    .slug(post.getSlug())
                    .title(post.getTitle())
                    .excerpt(post.getExcerpt())
                    .contentJson(post.getContentJson())
                    .author(authorFor(post.getAuthorUserId(), authors))
                    .visibility(post.getVisibility())
                    .projectId(post.getProjectId())
                    .category(post.getCategoryId() == null ? null : categories.get(post.getCategoryId()))
                    .publishedAt(post.getPublishedAt())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .viewerReaction(viewerReactions.get(post.getId()))
                    .reactionCounts(counts)
                    .reactionCount(counts.values().stream().mapToLong(Long::longValue).sum())
                    .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                    .build();
        }).toList();
        String nextCursor = hasMore ? encodeCursor(posts.getLast().getPublishedAt(), posts.getLast().getId()) : null;
        return new CursorPageResponse<>(items, nextCursor);
    }

    @Override
    @Transactional
    public PostReactionResponse setReaction(
            String authenticatedEmail, Long postId, PostReactionType reactionType
    ) {
        UserEntity viewer = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, viewer.getId());
        Instant now = Instant.now();
        PostReactionEntity reaction = postReactionRepository.findByPostIdAndUserId(postId, viewer.getId())
                .orElseGet(() -> PostReactionEntity.create(postId, viewer.getId(), reactionType, now));
        reaction.changeTo(reactionType, now);
        postReactionRepository.save(reaction);
        return reactionResponse(postId, reactionType);
    }

    @Override
    @Transactional
    public PostReactionResponse removeReaction(String authenticatedEmail, Long postId) {
        UserEntity viewer = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, viewer.getId());
        postReactionRepository.findByPostIdAndUserId(postId, viewer.getId())
                .ifPresent(postReactionRepository::delete);
        postReactionRepository.flush();
        return reactionResponse(postId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostCommentResponse> getComments(
            String authenticatedEmail, Long postId, String cursor, int requestedLimit
    ) {
        UserEntity viewer = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, viewer.getId());
        int limit = validatedLimit(requestedLimit);
        CursorKey cursorKey = cursor == null || cursor.isBlank() ? FIRST_PAGE : decodeCursor(cursor);
        List<PostCommentEntity> fetched = postCommentRepository.findActivePage(
                postId,
                cursorKey.instant(),
                cursorKey.id(),
                PageRequest.of(0, limit + 1)
        );
        boolean hasMore = fetched.size() > limit;
        List<PostCommentEntity> comments = hasMore ? fetched.subList(0, limit) : fetched;
        Map<Long, PostAuthorResponse> authors = findAuthorsForComments(comments);
        List<PostCommentResponse> items = comments.stream()
                .map(comment -> toCommentResponse(comment, authors.get(comment.getAuthorUserId())))
                .toList();
        String nextCursor = hasMore
                ? encodeCursor(comments.getLast().getCreatedAt(), comments.getLast().getId())
                : null;
        return new CursorPageResponse<>(items, nextCursor);
    }

    @Override
    @Transactional
    public PostCommentResponse createComment(
            String authenticatedEmail, Long postId, PostCommentRequest request
    ) {
        UserEntity author = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, author.getId());
        PostCommentEntity comment = postCommentRepository.save(PostCommentEntity.create(
                postId, author.getId(), normalizedContent(request.content()), Instant.now()
        ));
        return toCommentResponse(comment, toAuthorResponse(author));
    }

    @Override
    @Transactional
    public PostCommentResponse editComment(
            String authenticatedEmail, Long postId, Long commentId, PostCommentRequest request
    ) {
        UserEntity author = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, author.getId());
        PostCommentEntity comment = postCommentRepository.findActiveByIdAndPostId(commentId, postId)
                .orElseThrow(this::commentNotFound);
        if (!comment.getAuthorUserId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comment is not owned by authenticated user");
        }
        comment.edit(normalizedContent(request.content()), Instant.now());
        return toCommentResponse(comment, toAuthorResponse(author));
    }

    @Override
    @Transactional
    public void deleteComment(String authenticatedEmail, Long postId, Long commentId) {
        UserEntity author = resolveActiveUser(authenticatedEmail);
        requireReadablePublishedPost(postId, author.getId());
        PostCommentEntity comment = postCommentRepository.findActiveByIdAndPostId(commentId, postId)
                .orElseThrow(this::commentNotFound);
        if (!comment.getAuthorUserId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comment is not owned by authenticated user");
        }
        comment.softDelete(Instant.now());
    }

    private PostReactionResponse reactionResponse(Long postId, PostReactionType viewerReaction) {
        Map<PostReactionType, Long> counts = findReactionCounts(List.of(postId))
                .getOrDefault(postId, emptyReactionCounts());
        return new PostReactionResponse(
                postId,
                viewerReaction,
                counts,
                counts.values().stream().mapToLong(Long::longValue).sum()
        );
    }

    private PostEntity requireReadablePublishedPost(Long postId, Long viewerUserId) {
        PostEntity post = postRepository.findActiveById(postId).orElseThrow(this::postNotFound);
        if (post.getStatus() != PostStatus.PUBLISHED || !isReadable(post, activeProjectIdsOrNoMatch(viewerUserId))) {
            throw postNotFound();
        }
        return post;
    }

    private boolean isReadable(PostEntity post, List<Long> activeProjectIds) {
        return post.getVisibility() == PostVisibility.PUBLIC
                || post.getVisibility() == PostVisibility.LAB
                || (post.getVisibility() == PostVisibility.PROJECT
                && post.getProjectId() != null
                && activeProjectIds.contains(post.getProjectId()));
    }

    private UserEntity resolveActiveUser(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user is unavailable"
                ));
    }

    private List<Long> activeProjectIdsOrNoMatch(Long userId) {
        List<Long> ids = projectMemberRepository.findActiveProjectIdsByUserId(userId);
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private int validatedLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }
        return requestedLimit;
    }

    private String normalizedContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty() || normalized.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content must contain 1 to 5000 characters");
        }
        return normalized;
    }

    private CursorKey decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return new CursorKey(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private String encodeCursor(Instant instant, Long id) {
        String raw = instant.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Map<Long, PostReactionType> findViewerReactions(List<Long> postIds, Long viewerId) {
        if (postIds.isEmpty()) return Map.of();
        return postReactionRepository.findAllByPostIdInAndUserId(postIds, viewerId).stream()
                .collect(Collectors.toMap(PostReactionEntity::getPostId, PostReactionEntity::getReactionType));
    }

    private Map<Long, Map<PostReactionType, Long>> findReactionCounts(List<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        Map<Long, Map<PostReactionType, Long>> result = new HashMap<>();
        for (PostReactionRepository.ReactionCountView row : postReactionRepository.countByPostIds(postIds)) {
            result.computeIfAbsent(row.getPostId(), ignored -> emptyReactionCounts())
                    .put(row.getReactionType(), row.getCount());
        }
        return result;
    }

    private Map<Long, Long> findCommentCounts(List<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postCommentRepository.countActiveByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostCommentRepository.CommentCountView::getPostId,
                        PostCommentRepository.CommentCountView::getCount));
    }

    private Map<PostReactionType, Long> emptyReactionCounts() {
        Map<PostReactionType, Long> counts = new EnumMap<>(PostReactionType.class);
        for (PostReactionType type : PostReactionType.values()) counts.put(type, 0L);
        return counts;
    }

    private Map<Long, PostCategoryResponse> findCategories(List<PostEntity> posts) {
        Set<Long> ids = posts.stream().map(PostEntity::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        Map<Long, PostCategoryResponse> result = new HashMap<>();
        contentCategoryRepository.findAllById(ids).forEach(category -> result.put(category.getId(), toCategoryResponse(category)));
        return result;
    }

    private Map<Long, PostAuthorResponse> findAuthorsForPosts(List<PostEntity> posts) {
        return findAuthors(posts.stream().map(PostEntity::getAuthorUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    private Map<Long, PostAuthorResponse> findAuthorsForComments(List<PostCommentEntity> comments) {
        return findAuthors(comments.stream().map(PostCommentEntity::getAuthorUserId).collect(Collectors.toSet()));
    }

    private Map<Long, PostAuthorResponse> findAuthors(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, PostAuthorResponse> result = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> result.put(user.getId(), toAuthorResponse(user)));
        return result;
    }

    private PostAuthorResponse authorFor(Long authorId, Map<Long, PostAuthorResponse> authors) {
        return authorId == null ? null : authors.get(authorId);
    }

    private PostAuthorResponse toAuthorResponse(UserEntity user) {
        return user == null ? null : PostAuthorResponse.builder().userId(user.getUserId()).name(user.getName()).build();
    }

    private PostCategoryResponse toCategoryResponse(ContentCategoryEntity category) {
        return PostCategoryResponse.builder().id(category.getId()).code(category.getCode()).name(category.getName()).build();
    }

    private PostCommentResponse toCommentResponse(PostCommentEntity comment, PostAuthorResponse author) {
        return new PostCommentResponse(comment.getId(), comment.getContent(), author,
                comment.getCreatedAt(), comment.getUpdatedAt());
    }

    private ResponseStatusException postNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
    }

    private ResponseStatusException commentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
    }

    private record CursorKey(Instant instant, Long id) {
    }
}
