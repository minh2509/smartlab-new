package com.smartlab.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.response.PostCategoryResponse;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostVisibility;
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
        return PostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .excerpt(post.getExcerpt())
                .contentJson(post.getContentJson())
                .visibility(post.getVisibility())
                .status(post.getStatus())
                .category(toCategoryResponse(category))
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
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
