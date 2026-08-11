package com.smartlab.controller;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @PostMapping
    public PostDetailResponse createPost(Authentication authentication, @Valid @RequestBody CreatePostRequest request) {
        return postService.createPost(authentication.getName(), request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('posts.submit')")
    public PostDetailResponse submitPost(Authentication authentication, @PathVariable Long id) {
        return postService.submitForReview(authentication.getName(), id);
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAuthority('posts.review')")
    public PostDetailResponse reviewPost(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ReviewPostRequest request
    ) {
        return postService.reviewPost(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('posts.publish')")
    public PostDetailResponse publishPost(Authentication authentication, @PathVariable Long id) {
        return postService.publishPost(authentication.getName(), id);
    }

    @PostMapping("/{id}/direct-publish")
    @PreAuthorize("hasAuthority('posts.publish.direct')")
    public PostDetailResponse directPublish(Authentication authentication, @PathVariable Long id) {
        return postService.directPublishPost(authentication.getName(), id);
    }

    @GetMapping
    public List<PostSummaryResponse> getPosts(Authentication authentication) {
        return postService.getReadablePosts(authentication.getName());
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAuthority('posts.review')")
    public List<PostSummaryResponse> getReviewQueue(Authentication authentication) {
        return postService.getReviewablePosts(authentication.getName());
    }

    @GetMapping("/review-queue/{id}")
    @PreAuthorize("hasAuthority('posts.review')")
    public PostDetailResponse getReviewQueuePost(Authentication authentication, @PathVariable Long id) {
        return postService.getReviewablePost(authentication.getName(), id);
    }

    @GetMapping("/{slug}")
    public PostDetailResponse getPostBySlug(Authentication authentication, @PathVariable String slug) {
        return postService.getPostBySlug(authentication.getName(), slug);
    }

    @PatchMapping("/{id}")
    public PostDetailResponse updatePost(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        return postService.updatePost(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(Authentication authentication, @PathVariable Long id) {
        postService.deletePost(authentication.getName(), id);
    }
}
