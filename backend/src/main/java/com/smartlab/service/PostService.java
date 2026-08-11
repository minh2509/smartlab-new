package com.smartlab.service;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;

import java.util.List;

public interface PostService {
    PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request);

    PostDetailResponse updatePost(String authenticatedEmail, Long id, UpdatePostRequest request);

    PostDetailResponse submitForReview(String authenticatedEmail, Long id);

    PostDetailResponse reviewPost(String authenticatedEmail, Long postId, ReviewPostRequest request);

    PostDetailResponse publishPost(String authenticatedEmail, Long postId);

    PostDetailResponse directPublishPost(String authenticatedEmail, Long postId);

    void deletePost(String authenticatedEmail, Long id);

    List<PostSummaryResponse> getMyPosts(String authenticatedEmail);

    /** Legacy service-level read used by existing workflow tests; the HTTP feed uses PostSocialService. */
    List<PostSummaryResponse> getReadablePosts(String authenticatedEmail);

    List<PostSummaryResponse> getReviewablePosts(String authenticatedEmail);

    PostDetailResponse getReviewablePost(String authenticatedEmail, Long postId);

    /** authenticatedEmail is null only for the dedicated anonymous public permalink. */
    PostDetailResponse getPostBySlug(String authenticatedEmail, String slug);
}
