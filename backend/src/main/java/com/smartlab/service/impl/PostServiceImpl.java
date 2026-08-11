package com.smartlab.service.impl;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostCategoryResponse;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReviewEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationService;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.PostService;
import com.smartlab.service.PostContentRenderer;
import com.smartlab.service.PostSlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.smartlab.service.AuditVocabulary.POST;
import static com.smartlab.service.AuditVocabulary.POST_REVIEWED;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final UserRepository userRepository;
    private final ContentCategoryRepository contentCategoryRepository;
    private final PostRepository postRepository;
    private final PostReviewRepository postReviewRepository;
    private final PostSlugGenerator postSlugGenerator;
    private final PostCreateAttemptService postCreateAttemptService;
    private final PostContentRenderer postContentRenderer;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        ContentCategoryEntity category = resolveActiveCategory(request.getCategoryId());
        PostVisibility visibility = request.getVisibility() == null ? PostVisibility.LAB : request.getVisibility();
        Long projectId = resolveProjectId(author, visibility, request.getProjectId(), true);
        Map<String, Object> contentJson = request.getContentJson() == null
                ? new LinkedHashMap<>()
                : request.getContentJson();
        String contentHtml = postContentRenderer.renderAndSanitize(contentJson).orElse(null);
        Instant creationTime = Instant.now();
        for (int candidateNumber = 1; candidateNumber <= postSlugGenerator.maxCandidates(); candidateNumber++) {
            String candidate = postSlugGenerator.candidateFor(request.getTitle(), candidateNumber);
            if (postRepository.existsBySlug(candidate)) {
                continue;
            }

            PostEntity post = PostEntity.createDraft(
                    author.getId(),
                    request.getTitle(),
                    candidate,
                    request.getExcerpt(),
                    contentJson,
                    contentHtml,
                    visibility,
                    category == null ? null : category.getId(),
                    projectId,
                    creationTime
            );
            try {
                return toDetailResponse(postCreateAttemptService.persist(post), category);
            } catch (PostSlugCollisionException ignored) {
                // The isolated attempt has rolled back; advance to the next bounded candidate.
            }
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to allocate a unique post slug");
    }

    @Override
    @Transactional
    public PostDetailResponse updatePost(String authenticatedEmail, Long id, UpdatePostRequest request) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveByIdForUpdate(id)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() == null || !post.getAuthorUserId().equals(viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post is not owned by authenticated user");
        }
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft posts can be updated");
        }

        ContentCategoryEntity suppliedCategory = null;
        Long resolvedCategoryId = post.getCategoryId();
        if (request.hasCategoryId()) {
            suppliedCategory = resolveActiveCategory(request.getCategoryId());
            resolvedCategoryId = suppliedCategory == null ? null : suppliedCategory.getId();
        }

        String resolvedTitle = request.hasTitle() ? request.getTitle() : post.getTitle();
        String resolvedExcerpt = request.hasExcerpt() ? request.getExcerpt() : post.getExcerpt();
        Map<String, Object> resolvedContentJson = request.hasContentJson()
                ? request.getContentJson() == null ? new LinkedHashMap<>() : request.getContentJson()
                : post.getContentJson();
        String resolvedContentHtml = request.hasContentJson()
                ? postContentRenderer.renderAndSanitize(resolvedContentJson)
                        .orElse(post.getContentHtml())
                : post.getContentHtml();
        PostVisibility resolvedVisibility = request.hasVisibility() ? request.getVisibility() : post.getVisibility();
        Long requestedProjectId = request.hasProjectId() ? request.getProjectId() : post.getProjectId();
        Long resolvedProjectId = resolveProjectId(viewer, resolvedVisibility, requestedProjectId, false);
        Instant transitionInstant = Instant.now();

        post.applyDraftUpdate(
                resolvedTitle,
                resolvedExcerpt,
                resolvedContentJson,
                resolvedContentHtml,
                resolvedVisibility,
                resolvedCategoryId,
                resolvedProjectId,
                transitionInstant
        );

        PostCategoryResponse category = request.hasCategoryId()
                ? toCategoryResponse(suppliedCategory)
                : findCategoryResponse(resolvedCategoryId);
        return toDetailResponse(post, category);
    }

    @Override
    @Transactional
    public PostDetailResponse submitForReview(String authenticatedEmail, Long id) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveByIdForUpdate(id)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() == null || !post.getAuthorUserId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post is not owned by authenticated user");
        }
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft posts can be submitted for review");
        }

        post.submitForReview(Instant.now());
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    @Override
    @Transactional
    public PostDetailResponse reviewPost(String authenticatedEmail, Long postId, ReviewPostRequest request) {
        UserEntity reviewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() != null && post.getAuthorUserId().equals(reviewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post authors cannot review their own posts");
        }
        if (post.getStatus() != PostStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending-review posts can be reviewed");
        }

        Map<String, Object> auditBefore = Map.of("status", post.getStatus().name());
        Instant reviewInstant = Instant.now();
        PostReviewEntity review = PostReviewEntity.create(
                post.getId(),
                reviewer.getId(),
                request.decision(),
                request.reason(),
                reviewInstant
        );
        post.applyReviewDecision(request.decision(), reviewInstant);
        postReviewRepository.saveAndFlush(review);
        if (post.getAuthorUserId() != null) {
            notificationService.notify(
                    post.getAuthorUserId(),
                    reviewNotificationType(request.decision()),
                    reviewNotificationMessage(request.decision()),
                    new NotificationRelated(reviewer.getId(), "POST", post.getId(), null),
                    reviewInstant
            );
        }
        auditService.log(POST_REVIEWED, POST, post.getId().toString(), auditBefore,
                Map.of("status", post.getStatus().name(), "decision", request.decision().name()));

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    @Override
    @Transactional
    public PostDetailResponse publishPost(String authenticatedEmail, Long postId) {
        resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(this::postNotFound);

        if (post.getStatus() != PostStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved posts can be published");
        }

        post.publish(Instant.now());
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    @Override
    @Transactional
    public PostDetailResponse directPublishPost(String authenticatedEmail, Long postId) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() == null || !post.getAuthorUserId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post is not owned by authenticated user");
        }
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft posts can be directly published");
        }

        post.publishDirect(Instant.now());
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    @Override
    @Transactional
    public void deletePost(String authenticatedEmail, Long id) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        Instant transitionInstant = Instant.now();
        int affectedRows = postRepository.softDeleteOwnedDraft(id, author.getId(), transitionInstant);

        if (affectedRows == 1) {
            return;
        }
        if (affectedRows != 0) {
            throw new IllegalStateException("Unexpected soft delete affected-row count: " + affectedRows);
        }

        PostEntity post = postRepository.findActiveById(id)
                .orElseThrow(this::postNotFound);
        if (post.getAuthorUserId() == null || !post.getAuthorUserId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Post is not owned by authenticated user");
        }
        if (post.getStatus() != PostStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Post is not a draft");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Post deletion did not complete");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getReadablePosts(String authenticatedEmail) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        List<Long> activeProjectIds = activeProjectIdsOrNoMatch(viewer.getId());
        List<PostEntity> posts = postRepository.findActiveReadableByViewerUserId(viewer.getId(), activeProjectIds);
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);

        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getReviewablePosts(String authenticatedEmail) {
        UserEntity reviewer = resolveActiveAuthor(authenticatedEmail);
        List<PostEntity> posts = postRepository.findActivePendingReviewableByReviewerUserId(reviewer.getId());
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);

        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getReviewablePost(String authenticatedEmail, Long postId) {
        UserEntity reviewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActivePendingReviewableByIdAndReviewerUserId(
                        postId,
                        reviewer.getId()
                )
                .orElseThrow(this::postNotFound);

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPostBySlug(String authenticatedEmail, String slug) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveBySlug(slug)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() != null && post.getAuthorUserId().equals(viewer.getId())) {
            return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
        }
        if (!isReadableBy(post, activeProjectIdsOrNoMatch(viewer.getId()))) {
            throw postNotFound();
        }

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()));
    }

    private UserEntity resolveActiveAuthor(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is unavailable"));
    }

    private ContentCategoryEntity resolveActiveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return contentCategoryRepository.findByIdAndIsActiveTrue(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is unavailable"));
    }

    private Long resolveProjectId(
            UserEntity author,
            PostVisibility visibility,
            Long projectId,
            boolean rejectProjectIdForNonProjectVisibility
    ) {
        if (visibility != PostVisibility.PROJECT) {
            if (rejectProjectIdForNonProjectVisibility && projectId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "PUBLIC and LAB visibility must not include projectId");
            }
            return null;
        }
        if (projectId == null || projectId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PROJECT visibility requires projectId");
        }
        if (projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is unavailable");
        }
        if (!projectMemberRepository.existsByProject_IdAndUser_IdAndStatus(
                projectId,
                author.getId(),
                ProjectMemberStatus.ACTIVE
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not an active project member");
        }
        return projectId;
    }

    private List<Long> activeProjectIdsOrNoMatch(Long viewerUserId) {
        List<Long> activeProjectIds = projectMemberRepository.findActiveProjectIdsByUserId(viewerUserId);
        return activeProjectIds.isEmpty() ? List.of(-1L) : activeProjectIds;
    }

    private PostDetailResponse toDetailResponse(PostEntity post, ContentCategoryEntity category) {
        return toDetailResponse(post, toCategoryResponse(category));
    }

    private PostDetailResponse toDetailResponse(PostEntity post, PostCategoryResponse category) {
        return PostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .excerpt(post.getExcerpt())
                .contentJson(post.getContentJson())
                .visibility(post.getVisibility())
                .projectId(post.getProjectId())
                .status(post.getStatus())
                .category(category)
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private PostSummaryResponse toSummaryResponse(PostEntity post, PostCategoryResponse category) {
        return PostSummaryResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .excerpt(post.getExcerpt())
                .visibility(post.getVisibility())
                .projectId(post.getProjectId())
                .status(post.getStatus())
                .category(category)
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private Map<Long, PostCategoryResponse> findCategoryResponses(List<PostEntity> posts) {
        Set<Long> categoryIds = posts.stream()
                .map(PostEntity::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PostCategoryResponse> responsesById = new HashMap<>();
        contentCategoryRepository.findAllById(categoryIds)
                .forEach(category -> responsesById.put(category.getId(), toCategoryResponse(category)));
        return responsesById;
    }

    private PostCategoryResponse findCategoryResponse(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return contentCategoryRepository.findById(categoryId)
                .map(this::toCategoryResponse)
                .orElse(null);
    }

    private PostCategoryResponse categoryFor(PostEntity post, Map<Long, PostCategoryResponse> categoriesById) {
        return post.getCategoryId() == null ? null : categoriesById.get(post.getCategoryId());
    }

    private boolean isReadableBy(PostEntity post, List<Long> activeProjectIds) {
        return post.getStatus() == PostStatus.PUBLISHED
                && (post.getVisibility() == PostVisibility.PUBLIC
                || post.getVisibility() == PostVisibility.LAB
                || (post.getVisibility() == PostVisibility.PROJECT
                && post.getProjectId() != null
                && activeProjectIds.contains(post.getProjectId())));
    }

    private static String reviewNotificationMessage(ReviewDecision decision) {
        return switch (decision) {
            case APPROVED -> "Bài viết của bạn đã được duyệt.";
            case REVISION_REQUIRED -> "Bài viết của bạn cần được chỉnh sửa.";
            case REJECTED -> "Bài viết của bạn đã bị từ chối.";
        };
    }

    private static String reviewNotificationType(ReviewDecision decision) {
        return "POST_REVIEW_" + decision.name();
    }

    private ResponseStatusException postNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
    }

    private PostCategoryResponse toCategoryResponse(ContentCategoryEntity category) {
        if (category == null) {
            return null;
        }

        return PostCategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .build();
    }
}
