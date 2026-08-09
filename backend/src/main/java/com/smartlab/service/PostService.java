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

    void deletePost(String authenticatedEmail, Long id);

    List<PostSummaryResponse> getReadablePosts(String authenticatedEmail);

    PostDetailResponse getPostBySlug(String authenticatedEmail, String slug);
}
