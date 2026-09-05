package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabArticleRequest;
import com.smartlab.dto.request.UpdateLabArticleRequest;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabArticleEntity;
import com.smartlab.enums.LabArticleStatus;
import com.smartlab.repo.LabArticleRepository;
import com.smartlab.service.LabArticleSlugGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabArticleServiceImplTest {
    @Mock private LabArticleRepository articleRepository;
    @Mock private LabArticleSlugGenerator slugGenerator;
    @InjectMocks private LabArticleServiceImpl service;

    @Test void publicReadsUseDatabaseLimitsPaginationAndOnlyRepositoryPublishedMethods() {
        when(articleRepository.findNewestPublished(any(Pageable.class))).thenReturn(List.of(article(3L, LabArticleStatus.PUBLISHED, "2026-08-22T00:00:00Z")));
        when(articleRepository.findPublishedArchive(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(article(2L, LabArticleStatus.PUBLISHED, "2026-08-21T00:00:00Z")), PageRequest.of(2, 12), 1));
        assertThat(service.listLatest(3)).hasSize(1);
        PublicPageResponse<?> archive = service.listArchive(2, 12);
        assertThat(archive.page()).isEqualTo(2);
        ArgumentCaptor<Pageable> latest = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findNewestPublished(latest.capture());
        assertThat(latest.getValue().getPageNumber()).isZero(); assertThat(latest.getValue().getPageSize()).isEqualTo(3);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findPublishedArchive(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2); assertThat(page.getValue().getPageSize()).isEqualTo(12);
    }

    @Test void publicAndAdminPagingRejectInvalidBoundsBeforeQuerying() {
        assertBadRequest(() -> service.listLatest(0)); assertBadRequest(() -> service.listLatest(13));
        assertBadRequest(() -> service.listArchive(-1, 12)); assertBadRequest(() -> service.listArchive(0, 49));
        assertBadRequest(() -> service.listAdmin(-1, 20)); assertBadRequest(() -> service.listAdmin(0, 101));
        verifyNoInteractions(articleRepository);
    }

    @Test void publicDetailFindsOnlyActivePublishedAndDoesNotExposeAdminFields() {
        LabArticleEntity article = article(7L, LabArticleStatus.PUBLISHED, "2026-08-20T00:00:00Z");
        when(articleRepository.findBySlugAndStatusAndDeletedAtIsNull("article-7", LabArticleStatus.PUBLISHED)).thenReturn(Optional.of(article));
        var response = service.getPublicBySlug("article-7");
        assertThat(response.content()).containsEntry("type", "doc");
        assertThat(response.getClass().getRecordComponents()).extracting(component -> component.getName())
                .doesNotContain("status", "createdAt", "updatedAt", "deletedAt");
        when(articleRepository.findBySlugAndStatusAndDeletedAtIsNull("draft", LabArticleStatus.PUBLISHED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPublicBySlug("draft")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void createDraftAlwaysClearsPublishedAtWhetherStatusIsOmittedOrExplicit() {
        when(slugGenerator.generateUniqueSlug("Title")).thenReturn("title");
        when(articleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant supplied = Instant.parse("2026-08-20T00:00:00Z");
        CreateLabArticleRequest draft = create(LabArticleStatus.DRAFT, supplied); draft.setTitle(" Title ");
        var created = service.create(draft);
        assertThat(created).extracting(value -> value.title(), value -> value.slug(), value -> value.status(), value -> value.publishedAt())
                .containsExactly("Title", "title", LabArticleStatus.DRAFT, null);
        when(slugGenerator.generateUniqueSlug("Title")).thenReturn("title-2");
        assertThat(service.create(create(null, supplied))).extracting(value -> value.status(), value -> value.publishedAt())
                .containsExactly(LabArticleStatus.DRAFT, null);
    }

    @Test void createPublishedUsesNowWhenOmittedAndPreservesExplicitPublishedAt() {
        when(articleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(slugGenerator.generateUniqueSlug("Title")).thenReturn("title");
        CreateLabArticleRequest published = create(LabArticleStatus.PUBLISHED, null);
        assertThat(service.create(published).publishedAt()).isNotNull();
        Instant supplied = Instant.parse("2026-08-20T00:00:00Z");
        when(slugGenerator.generateUniqueSlug("Title")).thenReturn("title-2");
        assertThat(service.create(create(LabArticleStatus.PUBLISHED, supplied)).publishedAt()).isEqualTo(supplied);
    }

    @Test void createRejectsMissingContentAndExplicitDuplicateSlug() {
        CreateLabArticleRequest invalid = create(null, null); invalid.setContent(null); assertBadRequest(() -> service.create(invalid));
        CreateLabArticleRequest duplicate = create(null, null); duplicate.setSlug(" Bài viết ");
        when(slugGenerator.normalize("Bài viết")).thenReturn("bai-viet"); when(articleRepository.existsBySlugAndDeletedAtIsNull("bai-viet")).thenReturn(true);
        assertConflict(() -> service.create(duplicate)); verify(articleRepository, never()).saveAndFlush(any());
    }

    @Test void patchPreservesOmittedExcerptClearsExplicitNullAndKeepsSlugWhenTitleChanges() {
        LabArticleEntity article = article(7L, LabArticleStatus.PUBLISHED, "2026-08-20T00:00:00Z");
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article)); when(articleRepository.saveAndFlush(article)).thenReturn(article);
        UpdateLabArticleRequest update = new UpdateLabArticleRequest(); update.setTitle(" Changed ");
        service.update(7L, update); assertThat(article.getTitle()).isEqualTo("Changed"); assertThat(article.getSlug()).isEqualTo("article-7"); assertThat(article.getExcerpt()).isEqualTo("Excerpt");
        UpdateLabArticleRequest clear = new UpdateLabArticleRequest(); clear.setExcerpt(null); service.update(7L, clear); assertThat(article.getExcerpt()).isNull();
    }

    @Test void patchRejectsRequiredNullsAndAppliesPublishUnpublishRepublishSemantics() {
        LabArticleEntity article = article(7L, LabArticleStatus.DRAFT, null);
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article)); when(articleRepository.saveAndFlush(article)).thenReturn(article);
        UpdateLabArticleRequest nullTitle = new UpdateLabArticleRequest(); nullTitle.setTitle(null); assertBadRequest(() -> service.update(7L, nullTitle));
        UpdateLabArticleRequest nullContent = new UpdateLabArticleRequest(); nullContent.setContent(null); assertBadRequest(() -> service.update(7L, nullContent));
        UpdateLabArticleRequest publish = new UpdateLabArticleRequest(); publish.setStatus(LabArticleStatus.PUBLISHED); service.update(7L, publish); Instant historical = article.getPublishedAt(); assertThat(historical).isNotNull();
        UpdateLabArticleRequest unpublish = new UpdateLabArticleRequest(); unpublish.setStatus(LabArticleStatus.DRAFT); service.update(7L, unpublish); assertThat(article.getPublishedAt()).isEqualTo(historical);
        service.update(7L, publish); assertThat(article.getPublishedAt()).isEqualTo(historical);
    }

    @Test void deleteIsSoftAndAdminListUsesDatabasePaging() {
        LabArticleEntity article = article(7L, LabArticleStatus.DRAFT, null);
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article)); when(articleRepository.saveAndFlush(article)).thenReturn(article);
        service.delete(7L); assertThat(article.getDeletedAt()).isNotNull();
        when(articleRepository.findActiveForAdmin(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(article(8L, LabArticleStatus.DRAFT, null))));
        service.listAdmin(1, 20); ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class); verify(articleRepository).findActiveForAdmin(page.capture()); assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    private static CreateLabArticleRequest create(LabArticleStatus status, Instant publishedAt) { CreateLabArticleRequest request = new CreateLabArticleRequest(); request.setTitle("Title"); request.setContent(Map.of("type", "doc", "body", "Body")); request.setStatus(status); request.setPublishedAt(publishedAt); return request; }
    private static LabArticleEntity article(Long id, LabArticleStatus status, String publishedAt) { LabArticleEntity article = LabArticleEntity.create("Title", "article-" + id, "Excerpt", Map.of("type", "doc", "body", "Body"), status, publishedAt == null ? null : Instant.parse(publishedAt), Instant.parse("2026-08-01T00:00:00Z")); ReflectionTestUtils.setField(article, "id", id); return article; }
    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)); }
    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)); }
}
