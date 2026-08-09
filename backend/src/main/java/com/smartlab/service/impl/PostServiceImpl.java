package com.smartlab.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostCategoryResponse;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.PostStatus;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final UserRepository userRepository;
    private final ContentCategoryRepository contentCategoryRepository;
    private final PostRepository postRepository;
    private final PostSlugGenerator postSlugGenerator;

    @Override
    @Transactional
    public PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request) {
        UserEntity author = resolveActiveAuthor(authenticatedEmail);
        ContentCategoryEntity category = resolveActiveCategory(request.getCategoryId());
        JsonNode contentJson = request.getContentJson() == null
                ? JsonNodeFactory.instance.objectNode()
                : request.getContentJson();
        PostVisibility visibility = request.getVisibility() == null ? PostVisibility.LAB : request.getVisibility();
        Instant creationTime = Instant.now();
        PostEntity post = PostEntity.createDraft(
                author.getId(),
                request.getTitle(),
                postSlugGenerator.generateUniqueSlug(request.getTitle()),
                request.getExcerpt(),
                contentJson,
                visibility,
                category == null ? null : category.getId(),
                creationTime
        );

        return toDetailResponse(postRepository.save(post), category);
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
        JsonNode resolvedContentJson = request.hasContentJson()
                ? request.getContentJson() == null ? JsonNodeFactory.instance.objectNode() : request.getContentJson()
                : post.getContentJson();
        PostVisibility resolvedVisibility = request.hasVisibility() ? request.getVisibility() : post.getVisibility();
        Instant transitionInstant = Instant.now();

        post.applyDraftUpdate(
                resolvedTitle,
                resolvedExcerpt,
                resolvedContentJson,
                resolvedVisibility,
                resolvedCategoryId,
                transitionInstant
        );

        PostCategoryResponse category = request.hasCategoryId()
                ? toCategoryResponse(suppliedCategory)
                : findCategoryResponse(resolvedCategoryId);
        return toDetailResponse(post, category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryResponse> getReadablePosts(String authenticatedEmail) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        List<PostEntity> posts = postRepository.findActiveReadableByViewerUserId(viewer.getId());
        Map<Long, PostCategoryResponse> categoriesById = findCategoryResponses(posts);

        return posts.stream()
                .map(post -> toSummaryResponse(post, categoryFor(post, categoriesById)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPostBySlug(String authenticatedEmail, String slug) {
        UserEntity viewer = resolveActiveAuthor(authenticatedEmail);
        PostEntity post = postRepository.findActiveBySlug(slug)
                .orElseThrow(this::postNotFound);

        if (!isReadableBy(post, viewer.getId())) {
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

    private boolean isReadableBy(PostEntity post, Long viewerUserId) {
        return (post.getAuthorUserId() != null && post.getAuthorUserId().equals(viewerUserId))
                || (post.getStatus() == PostStatus.PUBLISHED
                && (post.getVisibility() == PostVisibility.PUBLIC || post.getVisibility() == PostVisibility.LAB));
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
