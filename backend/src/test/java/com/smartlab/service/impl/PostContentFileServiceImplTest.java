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
import com.smartlab.service.FileService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    @Mock private FileService fileService;

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(userRepository, categoryRepository, postRepository, reviewRepository,
                slugGenerator, createAttemptService, renderer, notificationService, auditService,
                projectRepository, projectMemberRepository);
        ReflectionTestUtils.setField(postService, "postContentFileService", contentFiles);
        ReflectionTestUtils.setField(postService, "fileService", fileService);
        ReflectionTestUtils.setField(postService, "postMediaCache", new PostMediaCache());
        lenient().when(fileService.canRead(anyLong(), nullable(Authentication.class))).thenReturn(true);
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
    void anonymousReadsPublicFileReferencedByPublishedPublicPostWhenFilePolicyAllowsIt() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        when(postRepository.findActivePublishedPublicBySlug("public-file")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(13L, AUTHOR_ID, "application/pdf", "public.pdf", false, "PUBLIC")));
        when(contentFiles.downloadActiveContent(13L)).thenReturn(
                new PostContentFileService.DownloadedContent(new byte[]{9}, "application/pdf", "public.pdf"));

        assertThat(postService.downloadPostFile(null, "public-file", 13L).content()).containsExactly((byte) 9);
        verify(fileService).canRead(13L, null);
    }

    @Test
    void anonymousCannotDownloadPrivateFileReferencedByPublishedPublicPost() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        when(postRepository.findActivePublishedPublicBySlug("public-private-file")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(13L, AUTHOR_ID, "application/pdf", "private.pdf", false, "PRIVATE")));
        when(fileService.canRead(13L, null)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "public-private-file", 13L));
        verify(contentFiles, never()).downloadActiveContent(13L);
    }

    @Test
    void anonymousCannotDownloadPrivateImageReferencedByPublishedPublicPost() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "image", "fileId", 14))));
        when(postRepository.findActivePublishedPublicBySlug("public-private-image")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(14L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(14L, AUTHOR_ID, "image/png", "private.png", true, "PRIVATE")));
        when(fileService.canRead(14L, null)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "public-private-image", 14L));
        verify(contentFiles, never()).downloadActiveContent(14L);
    }

    @Test
    void cachedPrivateImageStillRequiresFileAuthorizationForAnonymousCaller() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "image", "fileId", 16))));
        activeAuthor();
        when(postRepository.findActiveBySlug("public-cached-private-image")).thenReturn(Optional.of(publicPost));
        when(postRepository.findActivePublishedPublicBySlug("public-cached-private-image")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(16L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(16L, AUTHOR_ID, "image/png", "private.png", true, "PRIVATE")));
        when(contentFiles.downloadActiveContent(16L)).thenReturn(
                new PostContentFileService.DownloadedContent(new byte[]{6}, "image/png", "private.png"));
        Authentication author = authentication(AUTHOR_EMAIL);
        when(fileService.canRead(16L, author)).thenReturn(true);
        when(fileService.canRead(16L, null)).thenReturn(false);

        assertThat(postService.downloadPostFile(author, "public-cached-private-image", 16L).content())
                .containsExactly((byte) 6);
        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "public-cached-private-image", 16L));
        verify(contentFiles, times(1)).downloadActiveContent(16L);
    }

    @Test
    void unrelatedAuthenticatedUserCannotReadPrivateFileFromReadablePublicPost() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        activeViewer();
        when(postRepository.findActiveBySlug("public-private-file-viewer")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(13L, AUTHOR_ID, "application/pdf", "private.pdf", false, "PRIVATE")));
        Authentication viewer = authentication(VIEWER_EMAIL);
        when(fileService.canRead(13L, viewer)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(viewer, "public-private-file-viewer", 13L));
        verify(contentFiles, never()).downloadActiveContent(13L);
    }

    @Test
    void projectFileUsesEffectiveFilePolicyEvenWhenPostIsPublic() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        activeViewer();
        when(postRepository.findActivePublishedPublicBySlug("public-project-file")).thenReturn(Optional.of(publicPost));
        when(postRepository.findActiveBySlug("public-project-file")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(13L, AUTHOR_ID, "application/pdf", "project.pdf", false, "PROJECT")));
        when(contentFiles.downloadActiveContent(13L)).thenReturn(
                new PostContentFileService.DownloadedContent(new byte[]{8}, "application/pdf", "project.pdf"));
        when(fileService.canRead(13L, null)).thenReturn(false);
        Authentication viewer = authentication(VIEWER_EMAIL);
        when(fileService.canRead(13L, viewer)).thenReturn(true);

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "public-project-file", 13L));
        assertThat(postService.downloadPostFile(viewer, "public-project-file", 13L).content()).containsExactly((byte) 8);
        verify(fileService).canRead(13L, viewer);
    }

    @Test
    void labFileUsesEffectiveFilePolicyEvenWhenPostIsPublic() {
        PostEntity publicPost = post(PostStatus.PUBLISHED, PostVisibility.PUBLIC, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 15))));
        when(postRepository.findActivePublishedPublicBySlug("public-lab-file")).thenReturn(Optional.of(publicPost));
        when(contentFiles.findActiveMetadata(15L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(15L, AUTHOR_ID, "application/pdf", "lab.pdf", false, "LAB")));
        when(fileService.canRead(15L, null)).thenReturn(false);

        assertStatus(HttpStatus.NOT_FOUND, () -> postService.downloadPostFile(null, "public-lab-file", 15L));
        verify(contentFiles, never()).downloadActiveContent(15L);
    }

    @Test
    void postAuthorReadsPrivateFileWhenFilePolicyAllowsIt() {
        PostEntity draft = post(PostStatus.DRAFT, PostVisibility.LAB, AUTHOR_ID,
                Map.of("type", "doc", "body", "body", "files", List.of(Map.of("type", "file", "fileId", 13))));
        activeAuthor();
        when(postRepository.findActiveBySlug("private-draft-file")).thenReturn(Optional.of(draft));
        when(contentFiles.findActiveMetadata(13L)).thenReturn(Optional.of(
                new PostContentFileService.FileMetadata(13L, AUTHOR_ID, "application/pdf", "private.pdf", false, "PRIVATE")));
        when(contentFiles.downloadActiveContent(13L)).thenReturn(
                new PostContentFileService.DownloadedContent(new byte[]{7}, "application/pdf", "private.pdf"));
        Authentication author = authentication(AUTHOR_EMAIL);
        when(fileService.canRead(13L, author)).thenReturn(true);

        assertThat(postService.downloadPostFile(author, "private-draft-file", 13L).content()).containsExactly((byte) 7);
        verify(fileService).canRead(13L, author);
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

    @Test
    void authorizedPublisherReadsApprovedInlineMedia() {
        PostEntity approved = post(PostStatus.APPROVED, PostVisibility.LAB, AUTHOR_ID, files());
        activeViewer();
        when(postRepository.findActiveBySlug("approved")).thenReturn(Optional.of(approved));
        readableFile(12L);

        assertThat(postService.downloadPostFile(publisherAuthentication(), "approved", 12L).content())
                .containsExactly((byte) 1);
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
    private static Authentication publisherAuthentication() { Authentication auth = authentication(VIEWER_EMAIL); org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("posts.publish"))).when(auth).getAuthorities(); return auth; }
    private static PostEntity post(PostStatus status, PostVisibility visibility, long authorId, Map<String, Object> content) { PostEntity post = PostEntity.createDraft(authorId, "Title", "post", null, content, visibility, null, Instant.parse("2026-01-01T00:00:00Z")); set(post, "id", 55L); set(post, "status", status); return post; }
    private static void set(Object target, String field, Object value) { ReflectionTestUtils.setField(target, field, value); }
    private static void assertStatus(HttpStatus expected, Runnable work) { assertThatThrownBy(work::run).isInstanceOf(ResponseStatusException.class).extracting(error -> ((ResponseStatusException) error).getStatusCode()).isEqualTo(expected); }
}
