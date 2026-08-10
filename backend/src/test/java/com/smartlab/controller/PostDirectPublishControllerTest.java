package com.smartlab.controller;

import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostDirectPublishControllerTest {

    private static final String EMAIL = "admin@example.com";
    private static final Long POST_ID = 29L;

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
    void directPublishPostMappingReturnsCanonicalResponseAndForwardsPrincipalAndId() throws Exception {
        when(postService.directPublishPost(EMAIL, POST_ID)).thenReturn(response(PostStatus.PUBLISHED));

        mockMvc.perform(post("/posts/{id}/direct-publish", POST_ID).principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POST_ID))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(postService).directPublishPost(EMAIL, POST_ID);
    }

    @Test
    void directPublishMethodUsesOnlyPostMappingPathVariableAndExactCapabilityAuthority() throws Exception {
        Method directPublish = PostController.class.getMethod("directPublish", Authentication.class, Long.class);

        assertThat(directPublish.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/direct-publish");
        assertThat(directPublish.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('posts.publish.direct')");
        assertThat(hasAnnotation(directPublish.getParameterAnnotations()[1], PathVariable.class)).isTrue();
        assertThat(hasAnnotation(directPublish.getParameterAnnotations()[1], RequestBody.class)).isFalse();
        assertThat(Arrays.stream(directPublish.getAnnotations())
                .map(annotation -> annotation.annotationType())
                .anyMatch(type -> type == GetMapping.class || type == PatchMapping.class || type == DeleteMapping.class))
                .isFalse();
    }

    @Test
    void normalPublishRemainsSeparatelyMappedAndAuthorized() throws Exception {
        Method normalPublish = PostController.class.getMethod("publishPost", Authentication.class, Long.class);

        assertThat(normalPublish.getAnnotation(PostMapping.class).value()).containsExactly("/{id}/publish");
        assertThat(normalPublish.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('posts.publish')");
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
