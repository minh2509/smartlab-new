package com.smartlab.service.impl;

import com.smartlab.dto.request.CreatePostRequest;
import com.smartlab.dto.request.UpdatePostRequest;
import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.ContentCategoryRepository;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.PostReviewRepository;
import com.smartlab.repo.ProjectMemberRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AuditService;
import com.smartlab.service.NotificationService;
import com.smartlab.service.PostContentFileService;
import com.smartlab.service.PostMediaCache;
import com.smartlab.service.PostContentRenderer;
import com.smartlab.service.PostService;
import com.smartlab.service.PostSlugGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostContentFileServiceImplTest {
    private static final String AUTHOR_EMAIL = "author@example.edu";
    private static final String VIEWER_EMAIL = "viewer@example.edu";
    private static final long AUTHOR_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private ContentCategoryRepository categoryRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostReviewRepository reviewRepository;
    @Mock private PostSlugGenerator slugGenerator;
    @Mock private PostCreateAttemptService createAttemptService;
    @Mock private PostContentRenderer renderer;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private PostContentFileService contentFiles;

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(userRepository, categoryRepository, postRepository, reviewRepository,
                slugGenerator, createAttemptService, renderer, notificationService, auditService,
                projectRepository, projectMemberRepository);
        ReflectionTestUtils.setField(postService, "postContentFileService", contentFiles);
        ReflectionTestUtils.setField(postService, "postMediaCache", new PostMediaCache());
    }

    @Test
    void createAcceptsOwnedActiveImageAndFileReferences() {
        activeAuthor();
        CreatePostRequest request = createRequest(files());
        when(contentFiles.findActiveMetadata(12L)).thenReturn(Optional.of(metadata(12L, AUTHOR_ID, "image/png", true)));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(metadata(13L, AUTHOR_ID, "application/pdf", false)));
        when(renderer.renderAndSanitize(any())).thenReturn(Optional.of("<p>safe</p>"));
        when(slugGenerator.maxCandidates()).thenReturn(1);
        when(slugGenerator.candidateFor("Title", 1)).thenReturn("title");
        when(postRepository.existsBySlug("title")).thenReturn(false);
        when(createAttemptService.persist(any(PostEntity.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(postService.createPost(AUTHOR_EMAIL, request).getContentJson()).isEqualTo(request.getContentJson());
    }

    @Test
    void createRejectsMissingFile() {
        activeAuthor();
        assertCreateRejected(12L, Optional.empty());
    }

    @Test
    void createRejectsDeletedFileBecauseTheInternalSeamOnlyResolvesActiveMetadata() {
        activeAuthor();
        assertCreateRejected(12L, Optional.empty());
    }

    @Test
    void createRejectsAnotherUsersFileAndNonImageImageReference() {
        activeAuthor();
        assertCreateRejected(12L, Optional.of(metadata(12L, AUTHOR_ID + 1, "image/png", true)));
        assertCreateRejected(12L, Optional.of(metadata(12L, AUTHOR_ID, "image/png", false)));
    }

    @Test
    void unrelatedPatchPreservesExistingReferencesAndContentPatchValidatesNewReferences() {
        PostEntity post = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID, files());
        activeAuthor();
        when(postRepository.findActiveByIdForUpdate(55L)).thenReturn(Optional.of(post));
        UpdatePostRequest titlePatch = new UpdatePostRequest();
        titlePatch.setTitle("New title");

        postService.updatePost(AUTHOR_EMAIL, 55L, titlePatch);
        assertThat(post.getContentJson()).isEqualTo(files());
        verify(contentFiles, never()).findActiveMetadata(any());

        UpdatePostRequest contentPatch = new UpdatePostRequest();
        contentPatch.setContentJson(Map.of("type", "doc", "body", "changed", "files",
                List.of(Map.of("type", "image", "fileId", 99))));
        when(contentFiles.findActiveMetadata(99L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.BAD_REQUEST, () -> postService.updatePost(AUTHOR_EMAIL, 55L, contentPatch));
    }

    @Test
    void contentPatchValidatesReferencesAgainstThePostAuthorOwnershipInvariant() {
        PostEntity post = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID, Map.of("type", "doc", "body", "body"));
        activeAuthor();
        when(postRepository.findActiveByIdForUpdate(55L)).thenReturn(Optional.of(post));
        when(contentFiles.findActiveMetadata(12L)).thenReturn(Optional.of(metadata(12L, AUTHOR_ID, "image/png", true)));
        when(renderer.renderAndSanitize(any())).thenReturn(Optional.of("<p>body</p>"));
        UpdatePostRequest contentPatch = new UpdatePostRequest();
        contentPatch.setContentJson(Map.of("type", "doc", "body", "body", "files",
                List.of(Map.of("type", "image", "fileId", 12))));

        postService.updatePost(AUTHOR_EMAIL, 55L, contentPatch);

        verify(contentFiles).findActiveMetadata(12L);
    }

    @Test
    void anonymousOnlyReadsReferencedMediaFromPublishedPublicPost() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID, files());
        when(postRepository.findActivePublishedPublicBySlug("public-post")).thenReturn(Optional.of(publicPost));
        readableFile(12L);

        assertThat(postService.downloadPostFile(null, "public-post", 12L).content()).containsExactly((byte) 1);
        verify(postRepository, never()).findActiveBySlug(any());

        PostEntity draft = post(PostStatus.DRAFT, PostVisibility.PUBLIC, AUTHOR_ID, files());
        when(postRepository.findActivePublishedPublicBySlug("draft-intent")).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "draft-intent", 12L));
    }

    @Test
    void postAuthorReadsReferencedMediaFromOwnDraft() {
        PostEntity draft = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID, files());
        activeAuthor();
        when(postRepository.findActiveBySlug("draft")).thenReturn(Optional.of(draft));
        readableFile(12L);

        assertThat(postService.downloadPostFile(authentication(AUTHOR_EMAIL), "draft", 12L).content())
                .containsExactly((byte) 1);
    }

    @Test
    void postAuthorReadsReferencedMediaFromOwnPendingReviewPost() {
        PostEntity pending = post(PostStatus.PENDING_REVIEW, PostVisibility.LAB, AUTHOR_ID, files());
        activeAuthor();
        when(postRepository.findActiveBySlug("pending-owner")).thenReturn(Optional.of(pending));
        readableFile(12L);

        assertThat(postService.downloadPostFile(authentication(AUTHOR_EMAIL), "pending-owner", 12L).content())
                .containsExactly((byte) 1);
    }

    @Test
    void imageGatewayCachesSecondAuthorizedRequestButFileReferencesBypassCache() {
        PostEntity imagePost = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID, files());
        activeAuthor(); when(postRepository.findActiveBySlug("hot-image")).thenReturn(Optional.of(imagePost)); readableFile(12L);
        assertThat(postService.downloadPostFile(authentication(AUTHOR_EMAIL), "hot-image", 12L).content()).containsExactly((byte) 1);
        assertThat(postService.downloadPostFile(authentication(AUTHOR_EMAIL), "hot-image", 12L).content()).containsExactly((byte) 1);
        verify(contentFiles, times(1)).downloadActiveContent(12L);

        PostEntity filePost = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        when(postRepository.findActiveBySlug("plain-file")).thenReturn(Optional.of(filePost));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(metadata(13L, AUTHOR_ID, "application/pdf", false)));
        when(contentFiles.downloadActiveContent(13L)).thenReturn(new PostContentFileService.DownloadedContent(new byte[]{2}, "application/pdf", "file.pdf"));
        postService.downloadPostFile(authentication(AUTHOR_EMAIL), "plain-file", 13L);
        postService.downloadPostFile(authentication(AUTHOR_EMAIL), "plain-file", 13L);
        verify(contentFiles, times(2)).downloadActiveContent(13L);
    }

    @Test
    void hotCachedImageStillRequiresReferenceAndActiveOwnedMetadata() {
        PostEntity post = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID, files()); activeAuthor();
        when(postRepository.findActiveBySlug("mutated")).thenReturn(Optional.of(post));
        when(contentFiles.findActiveMetadata(12L)).thenReturn(Optional.of(metadata(12L, AUTHOR_ID, "image/png", true)), Optional.of(metadata(12L, AUTHOR_ID + 1, "image/png", true)), Optional.empty());
        readableDownloadOnly(12L);
        postService.downloadPostFile(authentication(AUTHOR_EMAIL), "mutated", 12L);
        ReflectionTestUtils.setField(post, "contentJson", Map.of("type", "doc", "body", "body"));
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(authentication(AUTHOR_EMAIL), "mutated", 12L));
        ReflectionTestUtils.setField(post, "contentJson", files());
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(authentication(AUTHOR_EMAIL), "mutated", 12L));
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(authentication(AUTHOR_EMAIL), "mutated", 12L));
        verify(contentFiles, times(1)).downloadActiveContent(12L);
    }

    @Test
    void labAuthenticatedAndProjectActiveMemberCanReadButAnonymousAndNonMemberCannot() {
        PostEntity labPost = post(PostStatus.PUBLISHED, PostVisibility.LAB, AUTHOR_ID, files());
        when(postRepository.findActivePublishedPublicBySlug("lab")).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "lab", 12L));
        activeViewer();
        when(postRepository.findActiveBySlug("lab")).thenReturn(Optional.of(labPost));
        when(projectMemberRepository.findActiveProjectIdsByUserId(8L)).thenReturn(List.of());
        readableFile(12L);
        assertThat(postService.downloadPostFile(authentication(VIEWER_EMAIL), "lab", 12L).mimeType()).isEqualTo("image/png");

        PostEntity project = post(PostStatus.PUBLISHED, PostVisibility.PROJECT, AUTHOR_ID, files());
        set(project, "projectId", 44L);
        when(postRepository.findActiveBySlug("project")).thenReturn(Optional.of(project));
        when(projectMemberRepository.findActiveProjectIdsByUserId(8L)).thenReturn(List.of(44L));
        assertThat(postService.downloadPostFile(authentication(VIEWER_EMAIL), "project", 12L).originalName()).isEqualTo("image.png");
        when(projectMemberRepository.findActiveProjectIdsByUserId(8L)).thenReturn(List.of());
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(authentication(VIEWER_EMAIL), "project", 12L));
    }

    @Test
    void hotCachedLabImageStillRequiresAnActiveAuthenticatedViewer() {
        PostEntity post = post(PostStatus.PUBLISHED, PostVisibility.LAB, AUTHOR_ID, files()); activeViewer();
        when(postRepository.findActiveBySlug("hot-lab")).thenReturn(Optional.of(post)); readableFile(12L);
        postService.downloadPostFile(authentication(VIEWER_EMAIL), "hot-lab", 12L);
        when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.UNAUTHORIZED, () -> postService.downloadPostFile(authentication(VIEWER_EMAIL), "hot-lab", 12L));
        verify(contentFiles, times(1)).downloadActiveContent(12L);
    }

    @Test
    void hotCachedProjectImageStillRequiresActiveMembership() {
        PostEntity post = post(PostStatus.PUBLISHED, PostVisibility.PROJECT, AUTHOR_ID, files()); set(post, "projectId", 44L); activeViewer();
        when(postRepository.findActiveBySlug("hot-project")).thenReturn(Optional.of(post));
        when(projectMemberRepository.findActiveProjectIdsByUserId(8L)).thenReturn(List.of(44L), List.of()); readableFile(12L);
        postService.downloadPostFile(authentication(VIEWER_EMAIL), "hot-project", 12L);
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(authentication(VIEWER_EMAIL), "hot-project", 12L));
        verify(contentFiles, times(1)).downloadActiveContent(12L);
    }

    @Test
    void hotCachedPendingImageStillRequiresReviewerAssignment() {
        PostEntity post = post(PostStatus.PENDING_REVIEW, PostVisibility.LAB, AUTHOR_ID, files()); activeViewer();
        when(postRepository.findActiveBySlug("hot-pending")).thenReturn(Optional.of(post));
        when(postRepository.findActivePendingReviewableByIdAndReviewerUserId(55L, 8L)).thenReturn(Optional.of(post), Optional.empty()); readableFile(12L);
        postService.downloadPostFile(reviewerAuthentication(), "hot-pending", 12L);
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(reviewerAuthentication(), "hot-pending", 12L));
        verify(contentFiles, times(1)).downloadActiveContent(12L);
    }

    @Test
    void authorizedReviewerReadsPendingMediaButUnrelatedUserAndUnreferencedFileDoNot() {
        PostEntity pending = post(PostStatus.PENDING_REVIEW, PostVisibility.LAB, AUTHOR_ID, files());
        activeViewer();
        when(postRepository.findActiveBySlug("pending")).thenReturn(Optional.of(pending));
        when(postRepository.findActivePendingReviewableByIdAndReviewerUserId(55L, 8L)).thenReturn(Optional.of(pending));
        readableFile(12L);
        assertThat(postService.downloadPostFile(reviewerAuthentication(), "pending", 12L).content()).containsExactly((byte) 1);
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(nonReviewerAuthentication(), "pending", 12L));
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(reviewerAuthentication(), "pending", 999L));
    }

    private void assertCreateRejected(Long id, Optional<PostContentFileService.FileMetadata> result) {
        when(contentFiles.findActiveMetadata(id)).thenReturn(result);
        assertStatus(HttpStatus.BAD_REQUEST, () -> postService.createPost(AUTHOR_EMAIL,
                createRequest(Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "image", "fileId", id))))));
    }

    private void readableFile(long id) {
        when(contentFiles.findActiveMetadata(id)).thenReturn(Optional.of(metadata(id, AUTHOR_ID, "image/png", true)));
        when(contentFiles.downloadActiveContent(id)).thenReturn(new PostContentFileService.DownloadedContent(new byte[]{1}, "image/png", "image.png"));
    }
    private void readableDownloadOnly(long id) { when(contentFiles.downloadActiveContent(id)).thenReturn(new PostContentFileService.DownloadedContent(new byte[]{1}, "image/png", "image.png")); }

    private void activeAuthor() { when(userRepository.findByEmail(AUTHOR_EMAIL)).thenReturn(Optional.of(user(AUTHOR_ID))); }
    private void activeViewer() { when(userRepository.findByEmail(VIEWER_EMAIL)).thenReturn(Optional.of(user(8L))); }
    private static UserEntity user(long id) { return UserEntity.builder().id(id).email(id == AUTHOR_ID ? AUTHOR_EMAIL : VIEWER_EMAIL).isActive(true).build(); }
    private static CreatePostRequest createRequest(Map<String, Object> content) { CreatePostRequest request = new CreatePostRequest(); request.setTitle("Title"); request.setContentJson(content); return request; }
    private static Map<String, Object> files() { return Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "image", "fileId", 12), Map.of("type", "file", "fileId", 13))); }
    private static PostContentFileService.FileMetadata metadata(long id, long owner, String mime, boolean image) { return new PostContentFileService.FileMetadata(id, owner, mime, "image.png", image); }
    private static Authentication authentication(String email) { Authentication auth = org.mockito.Mockito.mock(Authentication.class); when(auth.isAuthenticated()).thenReturn(true); when(auth.getName()).thenReturn(email); return auth; }
    private static Authentication nonReviewerAuthentication() { Authentication auth = authentication(VIEWER_EMAIL); org.mockito.Mockito.doReturn(List.of()).when(auth).getAuthorities(); return auth; }
    private static Authentication reviewerAuthentication() { Authentication auth = authentication(VIEWER_EMAIL); org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("posts.review"))).when(auth).getAuthorities(); return auth; }
    private static PostEntity post(PostStatus status, PostVisibility visibility, long authorId, Map<String, Object> content) { PostEntity post = PostEntity.createDraft(authorId, "Title", "post", null, content, visibility, null, Instant.parse("2026-01-01T00:00:00Z")); set(post, "id", 55L); set(post, "status", status); return post; }
    private static void set(Object target, String field, Object value) { ReflectionTestUtils.setField(target, field, value); }
    private static void assertStatus(HttpStatus expected, Runnable work) { assertThatThrownBy(work::run).isInstanceOf(ResponseStatusException.class).extracting(error -> ((ResponseStatusException) error).getStatusCode()).isEqualTo(expected); }
}
