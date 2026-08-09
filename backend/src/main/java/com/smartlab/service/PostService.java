package com.smartlab.service;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;

import java.util.List;

public interface PostService {
    PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request);

    List<PostSummaryResponse> getReadablePosts(String authenticatedEmail);

    PostDetailResponse getPostBySlug(String authenticatedEmail, String slug);
}
