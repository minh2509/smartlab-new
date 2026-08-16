package com.smartlab.controller;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.service.PostService;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostWorkflowControllerTest {

    private static final String EMAIL = "member@example.edu";
    private static final Long POST_ID = 17L;

    @Mock
    private PostService postService;

    private PostController postController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postController = new PostController(postService);
        mockMvc = MockMvcBuilders.standaloneSetup(postController).build();
    }

    @Test
    void submitDelegatesTrustedAuthenticationNameAndPostId() {
        Authentication authentication = authentication();
        PostDetailResponse response = response(PostStatus.PENDING_REVIEW);
        when(postService.submitForReview(EMAIL, POST_ID)).thenReturn(response);

        PostDetailResponse actual = postController.submitPost(authentication, POST_ID);

        assertThat(actual).isSameAs(response);
        verify(postService).submitForReview(EMAIL, POST_ID);
    }

    @Test
    void reviewDelegatesTrustedAuthenticationNamePostIdAndExactCanonicalRequest() {
        Authentication authentication = authentication();
        ReviewPostRequest request = new ReviewPostRequest(ReviewDecision.REJECTED, "  needs evidence  ");
        PostDetailResponse response = response(PostStatus.REJECTED);
        when(postService.reviewPost(EMAIL, POST_ID, request)).thenReturn(response);

        PostDetailResponse actual = postController.reviewPost(authentication, POST_ID, request);

        assertThat(actual).isSameAs(response);
        verify(postService).reviewPost(EMAIL, POST_ID, request);
    }

    @Test
    void publishDelegatesTrustedAuthenticationNameAndPostIdWithoutOwnershipInput() {
        Authentication authentication = authentication();
        PostDetailResponse response = response(PostStatus.PUBLISHED);
        when(postService.publishPost(EMAIL, POST_ID)).thenReturn(response);

        PostDetailResponse actual = postController.publishPost(authentication, POST_ID);

        assertThat(actual).isSameAs(response);
        verify(postService).publishPost(EMAIL, POST_ID);
    }

    @Test
    void workflowPostMappingsReturnOkAndSerializeCanonicalServiceResponses() throws Exception {
        when(postService.submitForReview(EMAIL, POST_ID)).thenReturn(response(PostStatus.PENDING_REVIEW));
        when(postService.reviewPost(any(), any(), any())).thenReturn(response(PostStatus.PUBLISHED));
        when(postService.publishPost(EMAIL, POST_ID)).thenReturn(response(PostStatus.PUBLISHED));

        mockMvc.perform(post("/posts/{id}/submit", POST_ID).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mockMvc.perform(post("/posts/{id}/reviews", POST_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"reason\":\"ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(post("/posts/{id}/publish", POST_ID).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void reviewMappingDeserializesCanonicalDecisionAndPreservesReasonExactly() throws Exception {
        when(postService.reviewPost(any(), any(), any())).thenReturn(response(PostStatus.REJECTED));

        mockMvc.perform(post("/posts/{id}/reviews", POST_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"  needs evidence  \"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewPostRequest> request = ArgumentCaptor.forClass(ReviewPostRequest.class);
        verify(postService).reviewPost(eq(EMAIL), eq(POST_ID), request.capture());
        assertThat(request.getValue().decision()).isEqualTo(ReviewDecision.REJECTED);
        assertThat(request.getValue().reason()).isEqualTo("  needs evidence  ");
    }

    @Test
    void reviewMappingRejectsMissingDecisionBeforeServiceDelegation() throws Exception {
        assertInvalidReviewBody("{\"reason\":\"missing decision\"}");
    }

    @Test
    void reviewMappingRejectsBlankRejectedReasonBeforeServiceDelegation() throws Exception {
        assertInvalidReviewBody("{\"decision\":\"REJECTED\",\"reason\":\"  \\t \\n  \"}");
    }

    @Test
    void reviewMappingRejectsReasonLongerThanOneThousandCharactersBeforeServiceDelegation() throws Exception {
        assertInvalidReviewBody("{\"decision\":\"APPROVED\",\"reason\":\"" + "a".repeat(1_001) + "\"}");
    }

    @Test
    void mapsWorkflowMethodsWithExactPathsValidationAndAuthorityExpressions() throws NoSuchMethodException {
        Method submit = PostController.class.getMethod("submitPost", Authentication.class, Long.class);
        Method review = PostController.class.getMethod(
                "reviewPost",
                Authentication.class,
                Long.class,
                ReviewPostRequest.class
        );
        Method publish = PostController.class.getMethod("publishPost", Authentication.class, Long.class);

        assertThat(submit.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/submit");
        assertThat(review.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/reviews");
        assertThat(publish.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/publish");
        assertThat(hasAnnotation(submit.getParameterAnnotations()[1], PathVariable.class)).isTrue();
        assertThat(hasAnnotation(review.getParameterAnnotations()[1], PathVariable.class)).isTrue();
        assertThat(hasAnnotation(review.getParameterAnnotations()[2], RequestBody.class)).isTrue();
        assertThat(hasAnnotation(review.getParameterAnnotations()[2], Valid.class)).isTrue();
        assertThat(submit.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('posts.submit')");
        assertThat(review.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('posts.review')");
        assertThat(publish.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('posts.publish')");
    }

    @Test
    void controllerDoesNotExposePublisherQueueMethods() {
        assertThatThrownBy(() -> PostController.class.getMethod("getPublishQueue", Authentication.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> PostController.class.getMethod(
                "getPublishQueuePost",
                Authentication.class,
                Long.class
        )).isInstanceOf(NoSuchMethodException.class);
    }

    private void assertInvalidReviewBody(String body) throws Exception {
        mockMvc.perform(post("/posts/{id}/reviews", POST_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    private static boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> type) {
        return Arrays.stream(annotations).anyMatch(annotation -> annotation.annotationType() == type);
    }

    private static Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(EMAIL);
        return authentication;
    }

    private static PostDetailResponse response(PostStatus status) {
        return PostDetailResponse.builder()
                .id(POST_ID)
                .title("Post")
                .slug("post")
                .visibility(PostVisibility.LAB)
                .status(status)
                .build();
    }
}
