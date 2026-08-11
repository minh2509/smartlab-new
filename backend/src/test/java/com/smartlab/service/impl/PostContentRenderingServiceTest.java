package com.smartlab.service.impl;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PostContentRenderer;
import com.smartlab.service.PostSlugGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostContentRenderingServiceTest {

    private static final String EMAIL = "author@example.com";
    private static final Long AUTHOR_ID = 42L;

    private UserRepository userRepository;
    private ContentCategoryRepository contentCategoryRepository;
    private PostRepository postRepository;
    private PostReviewRepository postReviewRepository;
    private PostSlugGenerator postSlugGenerator;
    private PostCreateAttemptService postCreateAttemptService;
    private PostContentRenderer postContentRenderer;
    private NotificationService notificationService;
    private AuditService auditService;
    private com.smartlab.repo.ProjectRepository projectRepository;
    private com.smartlab.repo.ProjectMemberRepository projectMemberRepository;

    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        contentCategoryRepository = mock(ContentCategoryRepository.class);
        postRepository = mock(PostRepository.class);
        postReviewRepository = mock(PostReviewRepository.class);
        postSlugGenerator = mock(PostSlugGenerator.class);
        postCreateAttemptService = mock(PostCreateAttemptService.class);
        postContentRenderer = mock(PostContentRenderer.class);
        notificationService = mock(NotificationService.class);
        auditService = mock(AuditService.class);
        projectRepository = mock(com.smartlab.repo.ProjectRepository.class);
        projectMemberRepository = mock(com.smartlab.repo.ProjectMemberRepository.class);

        service = new PostServiceImpl(
                userRepository,
                contentCategoryRepository,
                postRepository,
                postReviewRepository,
                postSlugGenerator,
                postCreateAttemptService,
                postContentRenderer,
                notificationService,
                auditService,
                projectRepository,
                projectMemberRepository
        );

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(AUTHOR_ID);
        when(user.getIsActive()).thenReturn(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void createRendersCanonicalJsonAndPersistsOnlyRendererOutput() {
        Map<String, Object> contentJson = new LinkedHashMap<>();
        contentJson.put("type", "doc");
        contentJson.put("body", "<script>client input is data, not trusted html</script>");

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Rendered post");
        request.setContentJson(contentJson);
        request.setVisibility(PostVisibility.LAB);

        when(postContentRenderer.renderAndSanitize(contentJson))
                .thenReturn(Optional.of("<p>server-safe</p>"));

        when(postSlugGenerator.maxCandidates()).thenReturn(1);
        when(postSlugGenerator.candidateFor("Rendered post", 1))
                .thenReturn("rendered-post");
        when(postRepository.existsBySlug("rendered-post")).thenReturn(false);
        when(postCreateAttemptService.persist(any(PostEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createPost(EMAIL, request);

        ArgumentCaptor<PostEntity> persisted =
                ArgumentCaptor.forClass(PostEntity.class);

        verify(postCreateAttemptService).persist(persisted.capture());
        verify(postContentRenderer).renderAndSanitize(contentJson);

        assertThat(persisted.getValue().getContentJson())
                .isEqualTo(contentJson);

        assertThat(persisted.getValue().getContentHtml())
                .isEqualTo("<p>server-safe</p>");
    }

    @Test
    void patchingContentJsonRendersAgainAndReplacesContentHtml() {
        Map<String, Object> original = Map.of(
                "type", "doc",
                "body", "old"
        );

        PostEntity post = PostEntity.createDraft(
                AUTHOR_ID,
                "Original",
                "original",
                null,
                original,
                "<p>old-safe</p>",
                PostVisibility.LAB,
                null,
                java.time.Instant.now()
        );

        when(postRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(post));

        Map<String, Object> replacement = Map.of(
                "type", "doc",
                "body", "new"
        );

        when(postContentRenderer.renderAndSanitize(replacement))
                .thenReturn(Optional.of("<p>new-safe</p>"));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setContentJson(replacement);

        service.updatePost(EMAIL, 10L, request);

        verify(postContentRenderer)
                .renderAndSanitize(replacement);

        assertThat(post.getContentJson())
                .isEqualTo(replacement);

        assertThat(post.getContentHtml())
                .isEqualTo("<p>new-safe</p>");
    }

    @Test
    void patchingNonContentFieldDoesNotRenderAndKeepsExistingHtml() {
        Map<String, Object> original = Map.of(
                "type", "doc",
                "body", "unchanged"
        );

        PostEntity post = PostEntity.createDraft(
                AUTHOR_ID,
                "Original",
                "original",
                "Old excerpt",
                original,
                "<p>existing-safe</p>",
                PostVisibility.LAB,
                null,
                java.time.Instant.now()
        );

        when(postRepository.findActiveByIdForUpdate(11L))
                .thenReturn(Optional.of(post));

        UpdatePostRequest request = new UpdatePostRequest();
        request.setExcerpt("New excerpt");

        service.updatePost(EMAIL, 11L, request);

        verifyNoInteractions(postContentRenderer);

        assertThat(post.getContentJson())
                .isEqualTo(original);

        assertThat(post.getContentHtml())
                .isEqualTo("<p>existing-safe</p>");
    }

    @Test
    void rendererFailurePropagatesBeforeCreatePersistence() {
        Map<String, Object> contentJson = Map.of(
                "type", "doc"
        );

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Failure case");
        request.setContentJson(contentJson);

        when(postContentRenderer.renderAndSanitize(contentJson))
                .thenThrow(new IllegalStateException("render failed"));

        assertThatThrownBy(() -> service.createPost(EMAIL, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("render failed");

        verify(postCreateAttemptService, never())
                .persist(any(PostEntity.class));
    }

    @Test
    void apiRequestsDoNotExposeClientControlledContentHtmlField() {
        assertThat(Arrays.stream(CreatePostRequest.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("contentHtml");

        assertThat(Arrays.stream(UpdatePostRequest.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("contentHtml");
    }


    @Test
    void patchingContentJsonKeepsExistingHtmlWhenRendererIsUnavailable() {
        Map<String, Object> original = Map.of(
                "type", "doc",
                "body", "old"
        );

        PostEntity post = PostEntity.createDraft(
                AUTHOR_ID,
                "Original",
                "original",
                null,
                original,
                "<p>existing-safe</p>",
                PostVisibility.LAB,
                null,
                java.time.Instant.now()
        );

        when(postRepository.findActiveByIdForUpdate(12L))
                .thenReturn(Optional.of(post));

        Map<String, Object> replacement = Map.of(
                "type", "doc",
                "body", "new"
        );

        when(postContentRenderer.renderAndSanitize(replacement))
                .thenReturn(Optional.empty());

        UpdatePostRequest request = new UpdatePostRequest();
        request.setContentJson(replacement);

        service.updatePost(EMAIL, 12L, request);

        verify(postContentRenderer)
                .renderAndSanitize(replacement);

        assertThat(post.getContentJson())
                .isEqualTo(replacement);

        assertThat(post.getContentHtml())
                .isEqualTo("<p>existing-safe</p>");
    }

    @Test
    void disabledProductionRendererDoesNotPretendRenderingExists() {
        DisabledPostContentRenderer renderer =
                new DisabledPostContentRenderer();

        assertThat(renderer.renderAndSanitize(
                Map.of("type", "doc")
        )).isEmpty();
    }
}
