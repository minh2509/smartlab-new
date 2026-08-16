package com.smartlab.controller;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.PostCommentRequest;
import com.smartlab.dto.request.PostReactionRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.CursorPageResponse;
import com.smartlab.dto.response.PostCommentResponse;
import com.smartlab.dto.response.PostFeedResponse;
import com.smartlab.dto.response.PostReactionResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSocialService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/posts")
public class PostController {
    private final PostService postService;
    private final PostSocialService postSocialService;

    @Autowired
    public PostController(PostService postService, Optional<PostSocialService> postSocialService) {
        this.postService = postService;
        this.postSocialService = postSocialService.orElse(null);
    }

    PostController(PostService postService) {
        this(postService, Optional.empty());
    }

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
    @PreAuthorize("hasAuthority('posts.publish.direct') or hasAuthority('PROJECT_MANAGE')")
    public PostDetailResponse directPublish(Authentication authentication, @PathVariable Long id) {
        return postService.directPublishPost(authentication.getName(), id);
    }

    @GetMapping
    public CursorPageResponse<PostFeedResponse> getPosts(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return postSocialService.getFeed(authentication == null ? null : authentication.getName(), cursor, limit);
    }

    public List<PostSummaryResponse> getPosts(Authentication authentication) {
        return postService.getReadablePosts(authentication.getName());
    }

    @GetMapping("/mine")
    public List<PostSummaryResponse> getMyPosts(Authentication authentication) {
        return postService.getMyPosts(authentication.getName());
    }

    @PutMapping("/{id}/reaction")
    public PostReactionResponse setReaction(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PostReactionRequest request
    ) {
        return postSocialService.setReaction(authentication.getName(), id, request.reaction());
    }

    @DeleteMapping("/{id}/reaction")
    public PostReactionResponse removeReaction(Authentication authentication, @PathVariable Long id) {
        return postSocialService.removeReaction(authentication.getName(), id);
    }

    @GetMapping("/{id}/comments")
    public CursorPageResponse<PostCommentResponse> getComments(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return postSocialService.getComments(authentication.getName(), id, cursor, limit);
    }

    @PostMapping("/{id}/comments")
    public PostCommentResponse createComment(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PostCommentRequest request
    ) {
        return postSocialService.createComment(authentication.getName(), id, request);
    }

    @PatchMapping("/{id}/comments/{commentId}")
    public PostCommentResponse editComment(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long commentId,
            @Valid @RequestBody PostCommentRequest request
    ) {
        return postSocialService.editComment(authentication.getName(), id, commentId, request);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long commentId
    ) {
        postSocialService.deleteComment(authentication.getName(), id, commentId);
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

    @GetMapping("/public/{slug}")
    public PostDetailResponse getPublicPostBySlug(@PathVariable String slug) {
        return postService.getPostBySlug(null, slug);
    }

    @GetMapping("/{slug}/files/{fileId}")
    public ResponseEntity<byte[]> downloadPostFile(
            Authentication authentication,
            @PathVariable String slug,
            @PathVariable Long fileId
    ) {
        PostService.PostFileDownload file = postService.downloadPostFile(authentication, slug, fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.mimeType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(file.content());
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
