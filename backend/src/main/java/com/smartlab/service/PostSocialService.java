package com.smartlab.service;

import com.smartlab.dto.request.PostCommentRequest;
import com.smartlab.dto.response.CursorPageResponse;
import com.smartlab.dto.response.PostCommentResponse;
import com.smartlab.dto.response.PostFeedResponse;
import com.smartlab.dto.response.PostReactionResponse;
import com.smartlab.enums.PostReactionType;

public interface PostSocialService {
    /** authenticatedEmail is null only for the anonymous read-only feed branch. */
    CursorPageResponse<PostFeedResponse> getFeed(String authenticatedEmail, String cursor, int limit);

    PostReactionResponse setReaction(String authenticatedEmail, Long postId, PostReactionType reaction);

    PostReactionResponse removeReaction(String authenticatedEmail, Long postId);

    CursorPageResponse<PostCommentResponse> getComments(
            String authenticatedEmail, Long postId, String cursor, int limit
    );

    PostCommentResponse createComment(String authenticatedEmail, Long postId, PostCommentRequest request);

    PostCommentResponse editComment(
            String authenticatedEmail, Long postId, Long commentId, PostCommentRequest request
    );

    void deleteComment(String authenticatedEmail, Long postId, Long commentId);
}
