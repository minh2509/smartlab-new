package com.smartlab.service.impl;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostCategoryResponse;
import com.smartlab.dto.response.PostAuthorResponse;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostReviewFeedbackResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.PostReviewEntity;
import com.smartlab.entity.ProjectMemberEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.ProjectMemberStatus;
import com.smartlab.enums.ProjectRole;
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
import com.smartlab.service.PostContentFileReferences;
import com.smartlab.service.PostContentFileService;
import com.smartlab.service.PostMediaCache;
import com.smartlab.service.PostSlugGenerator;
import com.smartlab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
    private static final String DIRECT_PUBLISH_PERMISSION = "posts.publish.direct";
    private static final String PROJECT_MANAGE_PERMISSION = "PROJECT_MANAGE";
    private static final String PROJECT_ANNOUNCEMENT_PUBLISHED = "PROJECT_ANNOUNCEMENT_PUBLISHED";
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

    @Autowired
    private PostContentFileService postContentFileService;
    @Autowired
    private PostMediaCache postMediaCache;
    @Autowired
    private PermissionService permissionService;

    @Override
    public PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        ContentCategoryEntity category = resolveActiveCategory(request.getCategoryId());
        PostVisibility visibility = request.getVisibility() == null ? PostVisibility.LAB : request.getVisibility();
        Long projectId = resolveProjectId(author, visibility, request.getProjectId(), true);
        Map<String, Object> contentJson = request.getContentJson() == null
                ? new LinkedHashMap<>()
                : request.getContentJson();
        validateContentFileReferences(contentJson, author.getId());
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
                return toDetailResponse(postCreateAttemptService.persist(post), category, toAuthorResponse(author));
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
        if (post.getStatus() != PostStatus.DRAFT && post.getStatus() != PostStatus.REVISION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or revision-required posts can be updated");
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
        if (request.hasContentJson()) {
            validateContentFileReferences(resolvedContentJson, post.getAuthorUserId());
        }
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
        return toDetailResponse(post, category, toAuthorResponse(viewer));
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
        if (post.getStatus() != PostStatus.DRAFT && post.getStatus() != PostStatus.REVISION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or revision-required posts can be submitted for review");
        }

        post.submitForReview(Instant.now());
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()), toAuthorResponse(author));
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
                    new NotificationRelated(reviewer.getId(), "POST", post.getId(), "/posts/" + post.getSlug()),
                    reviewInstant
            );
        }
        auditService.log(POST_REVIEWED, POST, post.getId().toString(), auditBefore,
                Map.of("status", post.getStatus().name(), "decision", request.decision().name()));

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()),
                findAuthorResponse(post.getAuthorUserId()));
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
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()),
                findAuthorResponse(post.getAuthorUserId()));
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

        Set<String> permissions = permissionService.getEffectivePermissionCodes(author);
        if (!permissions.contains(DIRECT_PUBLISH_PERMISSION)) {
            requireProjectLeaderDirectPublish(post, author, permissions);
        }

        Instant publicationInstant = Instant.now();
        post.publishDirect(publicationInstant);
        if (post.getVisibility() == PostVisibility.PROJECT) {
            notifyActiveProjectMembers(post, author, publicationInstant);
        }
        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()), toAuthorResponse(author));
    }

    private void requireProjectLeaderDirectPublish(PostEntity post, UserEntity author, Set<String> permissions) {
        if (post.getVisibility() != PostVisibility.PROJECT
                || post.getProjectId() == null
                || !permissions.contains(PROJECT_MANAGE_PERMISSION)
                || !projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRoleAndStatus(
                        post.getProjectId(),
                        author.getId(),
                        ProjectRole.LEADER,
                        ProjectMemberStatus.ACTIVE
                )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Direct publishing requires global permission or active leadership of this project");
        }
    }

    private void notifyActiveProjectMembers(PostEntity post, UserEntity author, Instant publicationInstant) {
        projectMemberRepository.findMembersForDisplay(post.getProjectId(), ProjectMemberStatus.ACTIVE).stream()
                .map(ProjectMemberEntity::getUser)
                .filter(member -> !member.getId().equals(author.getId()))
                .forEach(member -> notificationService.notify(
                        member.getId(),
                        PROJECT_ANNOUNCEMENT_PUBLISHED,
                        "A project announcement was published",
                        new NotificationRelated(author.getId(), "POST", post.getId(), "/posts/" + post.getSlug()),
                        publicationInstant
                ));
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
    public List<PostSummaryResponse> getMyPosts(String authenticatedEmail) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        List<PostEntity> posts = postRepository.findActiveOwnedByAuthorUserId(viewer.getId());
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);
        Map<Long, PostAuthorResponse> authorsById = findAuthorResponses(posts);

        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById), authorFor(post, authorsById)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getReadablePosts(String authenticatedEmail) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        List<PostEntity> posts = postRepository.findActiveReadableByViewerUserId(
                viewer.getId(), activeProjectIdsOrNoMatch(viewer.getId())
        );
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);
        Map<Long, PostAuthorResponse> authorsById = findAuthorResponses(posts);
        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById), authorFor(post, authorsById)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getReviewablePosts(String authenticatedEmail) {
        UserEntity reviewer = resolveActiveAuthor(authenticatedEmail);
        List<PostEntity> posts = postRepository.findActivePendingReviewableByReviewerUserId(reviewer.getId());
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);
        Map<Long, PostAuthorResponse> authorsById = findAuthorResponses(posts);

        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById), authorFor(post, authorsById)))
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

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()),
                findAuthorResponse(post.getAuthorUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPostBySlug(String authenticatedEmail, String slug) {
        if (authenticatedEmail == null) {
            PostEntity publicPost = postRepository.findActivePublishedPublicBySlug(slug)
                    .orElseThrow(this::postNotFound);
            return toDetailResponse(publicPost, findCategoryResponse(publicPost.getCategoryId()),
                    findAuthorResponse(publicPost.getAuthorUserId()));
        }

        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveBySlug(slug)
                .orElseThrow(this::postNotFound);

        if (post.getAuthorUserId() != null && post.getAuthorUserId().equals(viewer.getId())) {
            return toDetailResponse(post, findCategoryResponse(post.getCategoryId()),
                    findAuthorResponse(post.getAuthorUserId()), findReviewFeedbackForAuthor(post));
        }
        if (!isReadableBy(post, activeProjectIdsOrNoMatch(viewer.getId()))) {
            throw postNotFound();
        }

        return toDetailResponse(post, findCategoryResponse(post.getCategoryId()),
                findAuthorResponse(post.getAuthorUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PostFileDownload downloadPostFile(Authentication authentication, String slug, Long fileId) {
        PostEntity post = resolveReadablePostForFile(authentication, slug);
        PostContentFileReferences.Reference reference = PostContentFileReferences.parse(post.getContentJson()).stream()
                .filter(candidate -> candidate.fileId().equals(fileId))
                .findFirst()
                .orElseThrow(this::postNotFound);
        PostContentFileService.FileMetadata metadata = postContentFileService.findActiveMetadata(fileId)
                .filter(candidate -> Objects.equals(candidate.ownerUserId(), post.getAuthorUserId()))
                .orElseThrow(this::postNotFound);
        if ("image".equals(reference.type()) && !metadata.image()) {
            throw postNotFound();
        }
        if ("image".equals(reference.type())) {
            byte[] bytes = postMediaCache.getOrLoad(fileId,
                    () -> postContentFileService.downloadActiveContent(fileId).content());
            return new PostFileDownload(bytes, metadata.mimeType(), metadata.originalName());
        }
        PostContentFileService.DownloadedContent content = postContentFileService.downloadActiveContent(fileId);
        return new PostFileDownload(content.content(), content.mimeType(), content.originalName());
    }

    private PostEntity resolveReadablePostForFile(Authentication authentication, String slug) {
        if (!isAuthenticated(authentication)) {
            return postRepository.findActivePublishedPublicBySlug(slug).orElseThrow(this::postNotFound);
        }
        UserEntity viewer = resolveActiveAuthor(authentication.getName());
        PostEntity post = postRepository.findActiveBySlug(slug).orElseThrow(this::postNotFound);
        if (Objects.equals(post.getAuthorUserId(), viewer.getId())) {
            return post;
        }
        if (post.getStatus() == PostStatus.PENDING_REVIEW && isAuthorizedReviewer(authentication)
                && postRepository.findActivePendingReviewableByIdAndReviewerUserId(post.getId(), viewer.getId()).isPresent()) {
            return post;
        }
        if (!isReadableBy(post, activeProjectIdsOrNoMatch(viewer.getId()))) {
            throw postNotFound();
        }
        return post;
    }

    private void validateContentFileReferences(Map<String, Object> contentJson, Long authorUserId) {
        List<PostContentFileReferences.Reference> references = PostContentFileReferences.parse(contentJson);
        for (PostContentFileReferences.Reference reference : references) {
            PostContentFileService.FileMetadata file = postContentFileService.findActiveMetadata(reference.fileId())
                    .filter(candidate -> Objects.equals(candidate.ownerUserId(), authorUserId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Post content file reference is unavailable"));
            if ("image".equals(reference.type()) && !file.image()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image reference must use a supported image file");
            }
        }
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    private boolean isAuthorizedReviewer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("posts.review"::equals);
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

    private PostDetailResponse toDetailResponse(
            PostEntity post,
            ContentCategoryEntity category,
            PostAuthorResponse author
    ) {
        return toDetailResponse(post, toCategoryResponse(category), author);
    }

    private PostDetailResponse toDetailResponse(
            PostEntity post,
            PostCategoryResponse category,
            PostAuthorResponse author
    ) {
        return toDetailResponse(post, category, author, null);
    }

    private PostDetailResponse toDetailResponse(
            PostEntity post,
            PostCategoryResponse category,
            PostAuthorResponse author,
            PostReviewFeedbackResponse reviewFeedback
    ) {
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
                .author(author)
                .reviewFeedback(reviewFeedback)
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private PostReviewFeedbackResponse findReviewFeedbackForAuthor(PostEntity post) {
        ReviewDecision decision = switch (post.getStatus()) {
            case REVISION_REQUIRED -> ReviewDecision.REVISION_REQUIRED;
            case REJECTED -> ReviewDecision.REJECTED;
            default -> null;
        };
        if (decision == null || post.getId() == null) {
            return null;
        }
        return postReviewRepository.findFirstByPostIdAndDecisionOrderByCreatedAtDescIdDesc(post.getId(), decision)
                .map(review -> new PostReviewFeedbackResponse(
                        review.getDecision(), review.getReason(), review.getCreatedAt()))
                .orElse(null);
    }

    private PostSummaryResponse toSummaryResponse(
            PostEntity post,
            PostCategoryResponse category,
            PostAuthorResponse author
    ) {
        return PostSummaryResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .excerpt(post.getExcerpt())
                .visibility(post.getVisibility())
                .projectId(post.getProjectId())
                .status(post.getStatus())
                .category(category)
                .author(author)
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

    private Map<Long, PostAuthorResponse> findAuthorResponses(List<PostEntity> posts) {
        Set<Long> authorIds = posts.stream()
                .map(PostEntity::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PostAuthorResponse> responsesById = new HashMap<>();
        userRepository.findAllById(authorIds)
                .forEach(author -> responsesById.put(author.getId(), toAuthorResponse(author)));
        return responsesById;
    }

    private PostAuthorResponse findAuthorResponse(Long authorUserId) {
        if (authorUserId == null) {
            return null;
        }
        return userRepository.findById(authorUserId)
                .map(this::toAuthorResponse)
                .orElse(null);
    }

    private PostAuthorResponse authorFor(PostEntity post, Map<Long, PostAuthorResponse> authorsById) {
        return post.getAuthorUserId() == null ? null : authorsById.get(post.getAuthorUserId());
    }

    private PostAuthorResponse toAuthorResponse(UserEntity author) {
        if (author == null) {
            return null;
        }
        return PostAuthorResponse.builder()
                .userId(author.getUserId())
                .name(author.getName())
                .build();
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
            case APPROVED -> "Bài viết của bạn đã được duyệt và xuất bản.";
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
