package com.smartlab.service.impl;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.entity.ContentCategoryEntity;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.PostSlugGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentCategoryRepository contentCategoryRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostSlugGenerator postSlugGenerator;

    @Mock
    private PostCreateAttemptService postCreateAttemptService;

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                userRepository,
                contentCategoryRepository,
                postRepository,
                postSlugGenerator,
                postCreateAttemptService
        );
    }

    @Test
    void resolvesTrustedEmailToCanonicalActiveAuthorAndCreatesDraftWithDefaults() {
        CreatePostRequest request = request("My Post", null, null, null, null);
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.candidateFor("My Post", 1)).thenReturn("my-post");
        saveWithId(88L);

        PostDetailResponse response = postService.createPost("member@example.edu", request);

        ArgumentCaptor<PostEntity> savedPost = ArgumentCaptor.forClass(PostEntity.class);
        verify(postCreateAttemptService).persist(savedPost.capture());
        PostEntity post = savedPost.getValue();
        assertThat(post.getAuthorUserId()).isEqualTo(41L);
        assertThat(post.getContentJson()).isNotNull().isEmpty();
        assertThat(post.getVisibility()).isEqualTo(PostVisibility.LAB);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getProjectId()).isNull();
        assertThat(post.getCoverFileId()).isNull();
        assertThat(post.getContentHtml()).isNull();
        assertThat(post.getPublishedAt()).isNull();
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getUpdatedAt()).isEqualTo(post.getCreatedAt());
        assertThat(response.getId()).isEqualTo(88L);
        assertThat(response.getSlug()).isEqualTo("my-post");
        assertThat(response.getContentJson()).isEqualTo(post.getContentJson());
    }

    @Test
    void preservesSuppliedContentJsonAndPublicVisibility() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "doc");
        content.put("nested", new LinkedHashMap<>(Map.of("enabled", true)));
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.candidateFor("Public Post", 1)).thenReturn("public-post");
        saveWithId(89L);

        postService.createPost("member@example.edu", request("Public Post", "Excerpt", content, PostVisibility.PUBLIC, null));

        ArgumentCaptor<PostEntity> savedPost = ArgumentCaptor.forClass(PostEntity.class);
        verify(postCreateAttemptService).persist(savedPost.capture());
        assertThat(savedPost.getValue().getContentJson()).isEqualTo(content).isNotSameAs(content);
        assertThat(savedPost.getValue().getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(savedPost.getValue().getExcerpt()).isEqualTo("Excerpt");

        @SuppressWarnings("unchecked")
        Map<String, Object> requestNested = (Map<String, Object>) content.get("nested");
        requestNested.put("enabled", false);
        assertThat(((Map<?, ?>) savedPost.getValue().getContentJson().get("nested")).get("enabled"))
                .isEqualTo(true);
    }

    @Test
    void acceptsAnActiveCategoryAndMapsItsSummary() {
        ContentCategoryEntity category = category(7L, "NEWS", "News");
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(contentCategoryRepository.findByIdAndIsActiveTrue(7L)).thenReturn(Optional.of(category));
        when(postSlugGenerator.candidateFor("Categorized Post", 1)).thenReturn("categorized-post");
        saveWithId(90L);

        PostDetailResponse response = postService.createPost("member@example.edu", request("Categorized Post", null, null, null, 7L));

        ArgumentCaptor<PostEntity> savedPost = ArgumentCaptor.forClass(PostEntity.class);
        verify(postCreateAttemptService).persist(savedPost.capture());
        assertThat(savedPost.getValue().getCategoryId()).isEqualTo(7L);
        assertThat(response.getCategory())
                .extracting(categoryResponse -> categoryResponse.getId(), categoryResponse -> categoryResponse.getCode(), categoryResponse -> categoryResponse.getName())
                .containsExactly(7L, "NEWS", "News");
    }

    @Test
    void createsAnUncategorizedDraftWhenCategoryIsAbsentFromRequest() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.candidateFor("Uncategorized", 1)).thenReturn("uncategorized");
        saveWithId(91L);

        PostDetailResponse response = postService.createPost("member@example.edu", request("Uncategorized", null, null, null, null));

        ArgumentCaptor<PostEntity> savedPost = ArgumentCaptor.forClass(PostEntity.class);
        verify(postCreateAttemptService).persist(savedPost.capture());
        assertThat(savedPost.getValue().getCategoryId()).isNull();
        assertThat(response.getCategory()).isNull();
        verifyNoInteractions(contentCategoryRepository);
    }

    @Test
    void rejectsMissingCategoryWithoutSaving() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(contentCategoryRepository.findByIdAndIsActiveTrue(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost("member@example.edu", request("Post", null, null, null, 7L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(postCreateAttemptService);
        verifyNoInteractions(postSlugGenerator);
    }

    @Test
    void rejectsInactiveCategoryWithoutSaving() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(contentCategoryRepository.findByIdAndIsActiveTrue(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost("member@example.edu", request("Post", null, null, null, 8L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(postCreateAttemptService);
        verifyNoInteractions(postSlugGenerator);
    }

    @Test
    void rejectsUnresolvedTrustedUserWithoutCreatingPost() {
        when(userRepository.findByEmail("missing@example.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost("missing@example.edu", request("Post", null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(contentCategoryRepository, postCreateAttemptService, postRepository);
    }

    @Test
    void rejectsInactiveTrustedUserWithoutCreatingPost() {
        when(userRepository.findByEmail("inactive@example.edu")).thenReturn(Optional.of(inactiveUser(41L)));

        assertThatThrownBy(() -> postService.createPost("inactive@example.edu", request("Post", null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(contentCategoryRepository, postCreateAttemptService, postRepository);
    }

    @Test
    void createRequestHasNoClientAuthorField() {
        assertThat(Arrays.stream(CreatePostRequest.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("authorUserId", "id", "slug", "status");
    }

    @Test
    void createOrchestrationHasNoOuterTransaction() throws Exception {
        Transactional transactional = PostServiceImpl.class
                .getMethod("createPost", String.class, CreatePostRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Test
    void retriesNextCandidateAfterExactSlugCollision() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.maxCandidates()).thenReturn(1_000);
        when(postSlugGenerator.candidateFor("Same Title", 1)).thenReturn("same-title");
        when(postSlugGenerator.candidateFor("Same Title", 2)).thenReturn("same-title-2");
        when(postCreateAttemptService.persist(any(PostEntity.class)))
                .thenThrow(new PostSlugCollisionException(new DataIntegrityViolationException("slug collision")))
                .thenAnswer(invocation -> assignId(invocation.getArgument(0), 92L));

        PostDetailResponse response = postService.createPost(
                "member@example.edu",
                request("Same Title", null, null, null, null)
        );

        assertThat(response.getSlug()).isEqualTo("same-title-2");
        verify(postCreateAttemptService, times(2)).persist(any(PostEntity.class));
    }

    @Test
    void skipsPreexistingBaseAndRetriesCollisionOnSecondCandidate() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.maxCandidates()).thenReturn(1_000);
        when(postSlugGenerator.candidateFor("Same Title", 1)).thenReturn("same-title");
        when(postSlugGenerator.candidateFor("Same Title", 2)).thenReturn("same-title-2");
        when(postSlugGenerator.candidateFor("Same Title", 3)).thenReturn("same-title-3");
        when(postRepository.existsBySlug("same-title")).thenReturn(true);
        when(postCreateAttemptService.persist(any(PostEntity.class)))
                .thenThrow(new PostSlugCollisionException(new DataIntegrityViolationException("slug collision")))
                .thenAnswer(invocation -> assignId(invocation.getArgument(0), 93L));

        PostDetailResponse response = postService.createPost(
                "member@example.edu",
                request("Same Title", null, null, null, null)
        );

        assertThat(response.getSlug()).isEqualTo("same-title-3");
        verify(postCreateAttemptService, times(2)).persist(any(PostEntity.class));
    }

    @Test
    void propagatesNonSlugDataIntegrityViolationWithoutRetry() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.maxCandidates()).thenReturn(1_000);
        when(postSlugGenerator.candidateFor("Invalid FK", 1)).thenReturn("invalid-fk");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("foreign key failure");
        when(postCreateAttemptService.persist(any(PostEntity.class))).thenThrow(exception);

        assertThatThrownBy(() -> postService.createPost(
                "member@example.edu",
                request("Invalid FK", null, null, null, null)
        )).isSameAs(exception);

        verify(postCreateAttemptService).persist(any(PostEntity.class));
    }

    @Test
    void returnsConflictWhenAllBoundedCandidatesAreOccupied() {
        when(userRepository.findByEmail("member@example.edu")).thenReturn(Optional.of(activeUser(41L)));
        when(postSlugGenerator.maxCandidates()).thenReturn(3);
        when(postSlugGenerator.candidateFor("Occupied", 1)).thenReturn("occupied");
        when(postSlugGenerator.candidateFor("Occupied", 2)).thenReturn("occupied-2");
        when(postSlugGenerator.candidateFor("Occupied", 3)).thenReturn("occupied-3");
        when(postRepository.existsBySlug(any(String.class))).thenReturn(true);

        assertThatThrownBy(() -> postService.createPost(
                "member@example.edu",
                request("Occupied", null, null, null, null)
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(postCreateAttemptService);
    }

    private void saveWithId(Long id) {
        when(postSlugGenerator.maxCandidates()).thenReturn(1_000);
        when(postCreateAttemptService.persist(any(PostEntity.class)))
                .thenAnswer(invocation -> assignId(invocation.getArgument(0), id));
    }

    private PostEntity assignId(PostEntity post, Long id) throws ReflectiveOperationException {
        Field field = PostEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(post, id);
        return post;
    }

    private static UserEntity activeUser(Long id) {
        return UserEntity.builder().id(id).email("member@example.edu").isActive(true).build();
    }

    private static UserEntity inactiveUser(Long id) {
        return UserEntity.builder().id(id).email("inactive@example.edu").isActive(false).build();
    }

    private static ContentCategoryEntity category(Long id, String code, String name) {
        return ContentCategoryEntity.builder().id(id).code(code).name(name).isActive(true).build();
    }

    private static CreatePostRequest request(String title, String excerpt, Map<String, Object> contentJson,
                                             PostVisibility visibility, Long categoryId) {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle(title);
        request.setExcerpt(excerpt);
        request.setContentJson(contentJson);
        request.setVisibility(visibility);
        request.setCategoryId(categoryId);
        return request;
    }
}
