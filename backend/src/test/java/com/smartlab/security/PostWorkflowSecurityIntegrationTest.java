package com.smartlab.security;

import com.smartlab.config.CustomAuthenticationEntryPoint;
import com.smartlab.config.SecurityConfig;
import com.smartlab.controller.PostController;
import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.filter.JwtRequestFilter;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.PostService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        SecurityConfig.class,
        JwtRequestFilter.class,
        JwtUtil.class,
        CustomAuthenticationEntryPoint.class
})
@TestPropertySource(properties = "jwt.secret.key=t08-controlled-runtime-jwt-secret-key-for-tests-only")
class PostWorkflowSecurityIntegrationTest {

    private static final String EMAIL = "reviewer@example.test";
    private static final String SESSION_ID = "t08-session";
    private static final Long POST_ID = 17L;

    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @MockitoBean
    private PostService postService;
    @MockitoBean
    private AppUserDetailService appUserDetailService;
    @MockitoBean
    private UserSessionService userSessionService;

    @BeforeEach
    void activeControlledSession() {
        mockMvc = webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
        when(userSessionService.isSessionActive(SESSION_ID)).thenReturn(true);
    }

    @ParameterizedTest(name = "{0} without authentication returns 401")
    @MethodSource("workflowEndpoints")
    void unauthenticatedWorkflowRequestsReturnUnauthorizedBeforeService(WorkflowEndpoint endpoint) throws Exception {
        mockMvc.perform(request(endpoint, null))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService, appUserDetailService);
        verify(userSessionService, never()).touchSession(anyString());
    }

    @ParameterizedTest(name = "{0} without exact authority returns 403")
    @MethodSource("workflowEndpoints")
    void authenticatedJwtWithoutWorkflowAuthorityReturnsForbiddenBeforeService(WorkflowEndpoint endpoint) throws Exception {
        String token = tokenWithAuthorities();

        mockMvc.perform(request(endpoint, token))
                .andExpect(status().isForbidden());

        verifyAuthenticatedFilterPath();
        verifyNoInteractions(postService);
    }

    @ParameterizedTest(name = "{0} wrong authority does not substitute")
    @MethodSource("workflowEndpoints")
    void authenticatedJwtWithWrongAuthorityReturnsForbiddenBeforeService(WorkflowEndpoint endpoint) throws Exception {
        String token = tokenWithAuthorities(endpoint.wrongAuthority());

        mockMvc.perform(request(endpoint, token))
                .andExpect(status().isForbidden());

        verifyAuthenticatedFilterPath();
        verifyNoInteractions(postService);
    }

    @ParameterizedTest(name = "{0} exact authority reaches service")
    @MethodSource("workflowEndpoints")
    void authenticatedJwtWithExactAuthorityReachesControllerAndReturnsOk(WorkflowEndpoint endpoint) throws Exception {
        String token = tokenWithAuthorities(endpoint.requiredAuthority());
        stubSuccessfulService(endpoint);

        mockMvc.perform(request(endpoint, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(endpoint.responseStatus().name()));

        verifyAuthenticatedFilterPath();
        verifySuccessfulServiceDelegation(endpoint);
    }

    @ParameterizedTest(name = "direct publish rejects additional wrong authority {0}")
    @MethodSource("additionalDirectPublishWrongAuthorities")
    void directPublishRejectsAdditionalWrongWorkflowAuthorities(String authority) throws Exception {
        String token = tokenWithAuthorities(authority);

        mockMvc.perform(request(directPublishEndpoint(), token))
                .andExpect(status().isForbidden());

        verifyAuthenticatedFilterPath();
        verifyNoInteractions(postService);
    }

    @ParameterizedTest(name = "direct-only authority cannot invoke normal publish")
    @MethodSource("directPublishOnlyAuthority")
    void directPublishAuthorityDoesNotAuthorizeNormalPublish(String authority) throws Exception {
        String token = tokenWithAuthorities(authority);

        mockMvc.perform(request(normalPublishEndpoint(), token))
                .andExpect(status().isForbidden());

        verifyAuthenticatedFilterPath();
        verifyNoInteractions(postService);
    }

    @ParameterizedTest(name = "reviewer read {0} without authentication returns 401")
    @MethodSource("reviewerReadEndpoints")
    void unauthenticatedReviewerReadsReturnUnauthorizedBeforeService(
            String path,
            boolean detail
    ) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService, appUserDetailService);
        verify(userSessionService, never()).touchSession(anyString());
    }

    @ParameterizedTest(name = "reviewer read {0} without exact authority returns 403")
    @MethodSource("reviewerReadEndpoints")
    void reviewerReadsRejectMissingOrWrongAuthorityBeforeService(
            String path,
            boolean detail
    ) throws Exception {
        String token = tokenWithAuthorities("posts.submit");

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyAuthenticatedFilterPath();
        verifyNoInteractions(postService);
    }

    @ParameterizedTest(name = "reviewer read {0} with exact authority reaches service")
    @MethodSource("reviewerReadEndpoints")
    void reviewerReadsAllowExactAuthorityAndDelegateCanonicalIdentity(
            String path,
            boolean detail
    ) throws Exception {
        String token = tokenWithAuthorities("posts.review");
        if (detail) {
            when(postService.getReviewablePost(EMAIL, POST_ID))
                    .thenReturn(response(PostStatus.PENDING_REVIEW));
        } else {
            when(postService.getReviewablePosts(EMAIL))
                    .thenReturn(List.of(summaryResponse()));
        }

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        verifyAuthenticatedFilterPath();
        if (detail) {
            verify(postService).getReviewablePost(EMAIL, POST_ID);
        } else {
            verify(postService).getReviewablePosts(EMAIL);
        }
    }

    private String tokenWithAuthorities(String... authorities) {
        UserDetails userDetails = User.withUsername(EMAIL)
                .password("t08-not-used")
                .authorities(authorities)
                .build();
        when(appUserDetailService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        return jwtUtil.generateToken(userDetails, SESSION_ID);
    }

    private void verifyAuthenticatedFilterPath() {
        verify(appUserDetailService).loadUserByUsername(EMAIL);
        verify(userSessionService).isSessionActive(SESSION_ID);
        verify(userSessionService).touchSession(SESSION_ID);
    }

    private void stubSuccessfulService(WorkflowEndpoint endpoint) {
        PostDetailResponse response = response(endpoint.responseStatus());
        switch (endpoint.operation()) {
            case SUBMIT -> when(postService.submitForReview(EMAIL, POST_ID)).thenReturn(response);
            case REVIEW -> when(postService.reviewPost(any(), any(), any(ReviewPostRequest.class))).thenReturn(response);
            case PUBLISH -> when(postService.publishPost(EMAIL, POST_ID)).thenReturn(response);
            case DIRECT_PUBLISH -> when(postService.directPublishPost(EMAIL, POST_ID)).thenReturn(response);
        }
    }

    private void verifySuccessfulServiceDelegation(WorkflowEndpoint endpoint) {
        switch (endpoint.operation()) {
            case SUBMIT -> verify(postService).submitForReview(EMAIL, POST_ID);
            case REVIEW -> verify(postService).reviewPost(
                    EMAIL,
                    POST_ID,
                    new ReviewPostRequest(com.smartlab.enums.ReviewDecision.APPROVED, "ready")
            );
            case PUBLISH -> verify(postService).publishPost(EMAIL, POST_ID);
            case DIRECT_PUBLISH -> verify(postService).directPublishPost(EMAIL, POST_ID);
        }
    }

    private static MockHttpServletRequestBuilder request(WorkflowEndpoint endpoint, String token) {
        MockHttpServletRequestBuilder request = post(endpoint.path());
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
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

    private static PostSummaryResponse summaryResponse() {
        return PostSummaryResponse.builder()
                .id(POST_ID)
                .title("Post")
                .slug("post")
                .visibility(PostVisibility.LAB)
                .status(PostStatus.PENDING_REVIEW)
                .build();
    }

    private static Stream<Arguments> reviewerReadEndpoints() {
        return Stream.of(
                Arguments.of("/posts/review-queue", false),
                Arguments.of("/posts/review-queue/17", true)
        );
    }

    private static Stream<WorkflowEndpoint> workflowEndpoints() {
        return Stream.of(
                new WorkflowEndpoint(
                        Operation.SUBMIT,
                        "/posts/17/submit",
                        null,
                        "posts.submit",
                        "POST_MANAGE",
                        PostStatus.PENDING_REVIEW
                ),
                new WorkflowEndpoint(
                        Operation.REVIEW,
                        "/posts/17/reviews",
                        "{\"decision\":\"APPROVED\",\"reason\":\"ready\"}",
                        "posts.review",
                        "posts.submit",
                        PostStatus.PUBLISHED
                ),
                new WorkflowEndpoint(
                        Operation.PUBLISH,
                        "/posts/17/publish",
                        null,
                        "posts.publish",
                        "posts.review",
                        PostStatus.PUBLISHED
                ),
                directPublishEndpoint()
        );
    }

    private static WorkflowEndpoint directPublishEndpoint() {
        return new WorkflowEndpoint(
                Operation.DIRECT_PUBLISH,
                "/posts/17/direct-publish",
                null,
                "posts.publish.direct",
                "posts.publish",
                PostStatus.PUBLISHED
        );
    }

    private static WorkflowEndpoint normalPublishEndpoint() {
        return new WorkflowEndpoint(
                Operation.PUBLISH,
                "/posts/17/publish",
                null,
                "posts.publish",
                "posts.review",
                PostStatus.PUBLISHED
        );
    }

    private static Stream<String> additionalDirectPublishWrongAuthorities() {
        return Stream.of("posts.review");
    }

    private static Stream<String> directPublishOnlyAuthority() {
        return Stream.of("posts.publish.direct");
    }

    private enum Operation {
        SUBMIT,
        REVIEW,
        PUBLISH,
        DIRECT_PUBLISH
    }

    private record WorkflowEndpoint(
            Operation operation,
            String path,
            String body,
            String requiredAuthority,
            String wrongAuthority,
            PostStatus responseStatus
    ) {
        @Override
        public String toString() {
            return operation.name().toLowerCase();
        }
    }
}
