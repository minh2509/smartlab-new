package com.smartlab.controller;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PermissionService;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSocialService;
import com.smartlab.dto.response.CursorPageResponse;
import com.smartlab.dto.response.PostFeedResponse;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    private static final String EMAIL = "member@example.edu";

    @Mock
    private PostService postService;
    @Mock
    private PostSocialService postSocialService;

    private PostController postController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        postController = new PostController(postService, Optional.of(postSocialService));
        mockMvc = MockMvcBuilders.standaloneSetup(postController).build();
    }

    @Test
    void isRestControllerAtPostsPathWithPostDomainServiceDependencies() {
        RequestMapping requestMapping = PostController.class.getAnnotation(RequestMapping.class);

        assertThat(PostController.class.isAnnotationPresent(RestController.class)).isTrue();
        assertThat(requestMapping.value()).containsExactly("/posts");
        assertThat(PostController.class.getDeclaredFields()).extracting(Field::getType)
                .containsExactly(PostService.class, PostSocialService.class)
                .doesNotContain(PostRepository.class, UserRepository.class, ContentCategoryRepository.class, PermissionService.class);
    }

    @Test
    void createDelegatesExactRequestAndTrustedAuthenticationName() {
        Authentication authentication = authentication();
        CreatePostRequest request = createRequest();
        PostDetailResponse response = detailResponse();
        when(postService.createPost(EMAIL, request)).thenReturn(response);

        PostDetailResponse actual = postController.createPost(authentication, request);

        assertThat(actual).isSameAs(response);
        verify(postService).createPost(EMAIL, request);
    }

    @Test
    void listDelegatesTrustedAuthenticationNameAndReturnsServiceList() {
        Authentication authentication = authentication();
        List<PostSummaryResponse> response = List.of(summaryResponse());
        when(postService.getReadablePosts(EMAIL)).thenReturn(response);

        List<PostSummaryResponse> actual = postController.getPosts(authentication);

        assertThat(actual).isSameAs(response);
        verify(postService).getReadablePosts(EMAIL);
    }

    @Test
    void reviewerListDelegatesTrustedAuthenticationNameAndIsNotTreatedAsSlug() throws Exception {
        Authentication authentication = authentication();
        List<PostSummaryResponse> response = List.of(summaryResponse());
        when(postService.getReviewablePosts(EMAIL)).thenReturn(response);

        mockMvc.perform(get("/posts/review-queue").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
        verify(postService).getReviewablePosts(EMAIL);
        verify(postService, org.mockito.Mockito.never()).getPostBySlug(EMAIL, "review-queue");
    }

    @Test
    void reviewerDetailDelegatesTrustedAuthenticationNameAndPostId() throws Exception {
        Authentication authentication = authentication();
        when(postService.getReviewablePost(EMAIL, 17L)).thenReturn(detailResponse());

        mockMvc.perform(get("/posts/review-queue/17").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(postService).getReviewablePost(EMAIL, 17L);
    }

    @Test
    void detailDelegatesSlugAndTrustedAuthenticationName() {
        Authentication authentication = authentication();
        PostDetailResponse response = detailResponse();
        when(postService.getPostBySlug(EMAIL, "unchanged-slug")).thenReturn(response);

        PostDetailResponse actual = postController.getPostBySlug(authentication, "unchanged-slug");

        assertThat(actual).isSameAs(response);
        verify(postService).getPostBySlug(EMAIL, "unchanged-slug");
    }

    @Test
    void patchDelegatesExactRequestIdAndTrustedAuthenticationName() {
        Authentication authentication = authentication();
        UpdatePostRequest request = new UpdatePostRequest();
        request.setTitle("Updated title");
        PostDetailResponse response = detailResponse();
        when(postService.updatePost(EMAIL, 17L, request)).thenReturn(response);

        PostDetailResponse actual = postController.updatePost(authentication, 17L, request);

        assertThat(actual).isSameAs(response);
        verify(postService).updatePost(EMAIL, 17L, request);
    }

    @Test
    void deleteDelegatesIdAndTrustedAuthenticationNameExactlyOnce() {
        Authentication authentication = authentication();

        postController.deletePost(authentication, 17L);

        verify(postService).deletePost(EMAIL, 17L);
    }

    @Test
    void postMappingReturnsOkAndValidatesBody() throws Exception {
        when(postService.createPost(any(), any())).thenReturn(detailResponse());

        mockMvc.perform(post("/posts")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Created post"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.slug").value("unchanged-slug"));

        mockMvc.perform(post("/posts")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAndDetailMappingsReturnOkServiceResponses() throws Exception {
        when(postSocialService.getFeed(EMAIL, null, 15)).thenReturn(new CursorPageResponse<>(
                List.of(PostFeedResponse.builder().id(7L).slug("unchanged-slug").build()), null
        ));
        when(postService.getPostBySlug(EMAIL, "unchanged-slug")).thenReturn(detailResponse());

        mockMvc.perform(get("/posts").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7));
        mockMvc.perform(get("/posts/unchanged-slug").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("unchanged-slug"));
    }

    @Test
    void anonymousFeedDelegatesAbsentAuthenticatedIdentity() throws Exception {
        when(postSocialService.getFeed(null, null, 15)).thenReturn(new CursorPageResponse<>(
                List.of(PostFeedResponse.builder().id(7L).slug("public-post").build()), null
        ));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("public-post"));

        verify(postSocialService).getFeed(null, null, 15);
    }

    @Test
    void publicPermalinkDelegatesAbsentAuthenticatedIdentity() throws Exception {
        when(postService.getPostBySlug(null, "public-post")).thenReturn(detailResponse());

        mockMvc.perform(get("/posts/public/public-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("unchanged-slug"));

        verify(postService).getPostBySlug(null, "public-post");
    }

    @Test
    void patchMappingReturnsOkAndRejectsEmptyPatch() throws Exception {
        when(postService.updatePost(any(), any(), any())).thenReturn(detailResponse());

        mockMvc.perform(patch("/posts/17")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(patch("/posts/17")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMappingReturnsNoContentWithEmptyBody() throws Exception {
        mockMvc.perform(delete("/posts/17").principal(authentication()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(postService).deletePost(EMAIL, 17L);
    }

    @Test
    void representativeServiceResponseStatusesPropagateWithoutRemapping() {
        Authentication authentication = authentication();
        ResponseStatusException badRequest = new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad category");
        ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "not owner");
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "missing");
        ResponseStatusException conflict = new ResponseStatusException(HttpStatus.CONFLICT, "not draft");
        CreatePostRequest createRequest = createRequest();
        UpdatePostRequest updateRequest = new UpdatePostRequest();
        updateRequest.setTitle("Updated title");
        when(postService.createPost(EMAIL, createRequest)).thenThrow(badRequest);
        when(postService.updatePost(EMAIL, 17L, updateRequest)).thenThrow(forbidden);
        when(postService.getPostBySlug(EMAIL, "missing")).thenThrow(notFound);
        doThrow(conflict).when(postService).deletePost(EMAIL, 17L);

        assertThatThrownBy(() -> postController.createPost(authentication, createRequest)).isSameAs(badRequest);
        assertThatThrownBy(() -> postController.updatePost(authentication, 17L, updateRequest)).isSameAs(forbidden);
        assertThatThrownBy(() -> postController.getPostBySlug(authentication, "missing")).isSameAs(notFound);
        assertThatThrownBy(() -> postController.deletePost(authentication, 17L)).isSameAs(conflict);
    }

    @Test
    void mapsExpectedMethodsAndValidatesRequestBodies() throws NoSuchMethodException {
        Method create = PostController.class.getMethod("createPost", Authentication.class, CreatePostRequest.class);
        Method list = PostController.class.getMethod("getPosts", Authentication.class, String.class, int.class);
        Method detail = PostController.class.getMethod("getPostBySlug", Authentication.class, String.class);
        Method patch = PostController.class.getMethod("updatePost", Authentication.class, Long.class, UpdatePostRequest.class);
        Method delete = PostController.class.getMethod("deletePost", Authentication.class, Long.class);

        assertThat(create.getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(list.getAnnotation(GetMapping.class).value()).isEmpty();
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{slug}");
        assertThat(patch.getAnnotation(PatchMapping.class).value()).containsExactly("/{id}");
        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{id}");
        assertThat(hasValidAnnotation(create.getParameterAnnotations()[1])).isTrue();
        assertThat(hasValidAnnotation(patch.getParameterAnnotations()[2])).isTrue();
    }

    @Test
    void mapsReviewerReadsWithExactStaticPathsAndAuthority() throws NoSuchMethodException {
        Method list = PostController.class.getMethod("getReviewQueue", Authentication.class);
        Method detail = PostController.class.getMethod("getReviewQueuePost", Authentication.class, Long.class);

        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/review-queue");
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/review-queue/{id}");
        assertThat(list.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('posts.review')");
        assertThat(detail.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('posts.review')");
    }

    private static boolean hasValidAnnotation(Annotation[] annotations) {
        return java.util.Arrays.stream(annotations).anyMatch(annotation -> annotation.annotationType() == Valid.class);
    }

    private static Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(EMAIL);
        return authentication;
    }

    private static CreatePostRequest createRequest() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Created post");
        request.setContentJson(Map.of("type", "doc"));
        return request;
    }

    private static PostDetailResponse detailResponse() {
        return PostDetailResponse.builder()
                .id(7L)
                .title("Post")
                .slug("unchanged-slug")
                .visibility(PostVisibility.LAB)
                .status(PostStatus.DRAFT)
                .build();
    }

    private static PostSummaryResponse summaryResponse() {
        return PostSummaryResponse.builder()
                .id(7L)
                .title("Post")
                .slug("unchanged-slug")
                .visibility(PostVisibility.LAB)
                .status(PostStatus.DRAFT)
                .build();
    }
}
