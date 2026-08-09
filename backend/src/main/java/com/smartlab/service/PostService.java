package com.smartlab.service;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;

public interface PostService {
    PostDetailResponse createPost(String authenticatedEmail, CreatePostRequest request);
}
