package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabNewsArticleRequest;
import com.smartlab.dto.request.UpdateLabNewsArticleRequest;
import com.smartlab.dto.response.LabNewsArticleResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.entity.LabNewsArticleEntity;
import com.smartlab.enums.PublicNewsSort;
import com.smartlab.repo.LabNewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabNewsArticleServiceImplTest {
    @Mock private LabNewsArticleRepository articleRepository;
    @InjectMocks private LabNewsArticleServiceImpl service;

    @Test void publicReadUsesDatabaseNewestFirstOrderingAndDatabaseLimit() {
        when(articleRepository.findNewestPublic(any(Pageable.class))).thenReturn(List.of(
                article(4L, "2026-08-22T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(3L, "2026-08-21T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(2L, "2026-08-20T00:00:00Z", "2026-08-01T00:00:00Z")));

        List<LabNewsArticleResponse> result = service.listPublic(3);

        assertThat(result).extracting(LabNewsArticleResponse::id).containsExactly(4L, 3L, 2L);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findNewestPublic(page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test void publicLimitRejectsValuesOutsideOneThroughTwelveBeforeQuerying() {
        assertBadRequest(() -> service.listPublic(0));
        assertBadRequest(() -> service.listPublic(13));
        verifyNoInteractions(articleRepository);
    }

    @Test void publicArchiveUsesDatabasePaginationAndPreservesPageMetadata() {
        when(articleRepository.findPublicArchive(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                article(4L, "2026-08-22T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(3L, "2026-08-21T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(2L, "2026-08-20T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(1L, "2026-08-19T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(0L, "2026-08-18T00:00:00Z", "2026-08-01T00:00:00Z"),
                article(-1L, "2026-08-17T00:00:00Z", "2026-08-01T00:00:00Z")), PageRequest.of(2, 12), 30));

        PublicPageResponse<LabNewsArticleResponse> result = service.listPublicArchive(2, 12);

        assertThat(result.items()).extracting(LabNewsArticleResponse::id).containsExactly(4L, 3L, 2L, 1L, 0L, -1L);
        assertThat(result).extracting(PublicPageResponse::page, PublicPageResponse::size, PublicPageResponse::totalElements, PublicPageResponse::totalPages)
                .containsExactly(2, 12, 30L, 3);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findPublicArchive(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2); assertThat(page.getValue().getPageSize()).isEqualTo(12);
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test void publicArchiveRejectsInvalidPageAndSizeBeforeQuerying() {
        assertBadRequest(() -> service.listPublicArchive(-1, 12));
        assertBadRequest(() -> service.listPublicArchive(0, 0));
        assertBadRequest(() -> service.listPublicArchive(0, 49));
        verifyNoInteractions(articleRepository);
    }

    @Test void publicArchiveForwardsNormalizedFiltersAndSortsInDatabase() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 12), 0));

        PublicPageResponse<LabNewsArticleResponse> result = service.listPublicArchive("  Robot ", " VnExpress ", 2026,
                PublicNewsSort.OLDEST, 1, 12);

        assertThat(result.items()).isEmpty();
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getPageSize()).isEqualTo(12);
        assertThat(page.getValue().getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
        assertThat(page.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    @Test void publicOptionsTrimDeduplicateCaseInsensitivelyAndPreserveDescendingYears() {
        when(articleRepository.findPublicSources()).thenReturn(List.of(" VnExpress ", "vnexpress", "SmartLab", " "));
        when(articleRepository.findPublicYears()).thenReturn(List.of(2026, 2025));

        assertThat(service.listPublicSources()).containsExactly("SmartLab", "VnExpress");
        assertThat(service.listPublicYears()).containsExactly(2026, 2025);
        verify(articleRepository).findPublicSources();
        verify(articleRepository).findPublicYears();
    }

    @Test void adminReadUsesDatabasePaginationAndIncludesBothVisibilityStates() {
        LabNewsArticleEntity publicArticle = article(4L, "2026-08-22T00:00:00Z", "2026-08-01T00:00:00Z");
        LabNewsArticleEntity privateArticle = article(3L, "2026-08-21T00:00:00Z", "2026-08-01T00:00:00Z");
        ReflectionTestUtils.setField(privateArticle, "isPublic", false);
        when(articleRepository.findActiveForAdmin(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(publicArticle, privateArticle), PageRequest.of(1, 20), 22));

        PublicPageResponse<LabNewsArticleResponse> result = service.listAdmin(1, 20);

        assertThat(result.items()).extracting(LabNewsArticleResponse::id).containsExactly(4L, 3L);
        assertThat(result.items()).extracting(LabNewsArticleResponse::isPublic).containsExactly(true, false);
        assertThat(result).extracting(PublicPageResponse::page, PublicPageResponse::size, PublicPageResponse::totalElements, PublicPageResponse::totalPages)
                .containsExactly(1, 20, 22L, 2);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findActiveForAdmin(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1); assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test void adminReadRejectsInvalidPageAndSizeBeforeQuerying() {
        assertBadRequest(() -> service.listAdmin(-1, 20));
        assertBadRequest(() -> service.listAdmin(0, 0));
        assertBadRequest(() -> service.listAdmin(0, 101));
        verifyNoInteractions(articleRepository);
    }

    @Test void createTrimsValuesDefaultsPrivateAndAcceptsHttpAndHttps() {
        when(articleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            LabNewsArticleEntity article = invocation.getArgument(0);
            ReflectionTestUtils.setField(article, "id", 9L);
            return article;
        });
        CreateLabNewsArticleRequest request = request(" http://example.test/news ");
        request.setTitle(" Article "); request.setSourceName(" Source "); request.setExcerpt(" Excerpt ");

        LabNewsArticleResponse created = service.create(request);

        assertThat(created).extracting(LabNewsArticleResponse::title, LabNewsArticleResponse::excerpt,
                LabNewsArticleResponse::sourceName, LabNewsArticleResponse::sourceUrl, LabNewsArticleResponse::isPublic)
                .containsExactly("Article", "Excerpt", "Source", "http://example.test/news", false);
        request.setSourceUrl("https://example.test/news");
        service.create(request);
    }

    @Test void createRejectsMissingRequiredFieldsAndInvalidUrls() {
        CreateLabNewsArticleRequest request = request("ftp://example.test/news");
        assertBadRequest(() -> service.create(request));
        request.setSourceUrl("https:///missing-host"); assertBadRequest(() -> service.create(request));
        request.setSourceUrl("https://example.test/news"); request.setTitle(" "); assertBadRequest(() -> service.create(request));
        request.setTitle("Title"); request.setSourceName(" "); assertBadRequest(() -> service.create(request));
        request.setSourceName("Source"); request.setSourceUrl(null); assertBadRequest(() -> service.create(request));
        request.setSourceUrl("https://example.test/news"); request.setPublishedAt(null); assertBadRequest(() -> service.create(request));
        verify(articleRepository, never()).saveAndFlush(any());
    }

    @Test void patchUpdatesSuppliedFieldsPreservesOmittedExcerptAndClearsExplicitNull() {
        LabNewsArticleEntity article = article(7L, "2026-08-20T00:00:00Z", "2026-08-01T00:00:00Z");
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article));
        when(articleRepository.saveAndFlush(article)).thenReturn(article);
        UpdateLabNewsArticleRequest update = new UpdateLabNewsArticleRequest();
        update.setTitle(" Updated "); update.setSourceName(" Updated source ");
        update.setSourceUrl("https://example.test/updated"); update.setPublishedAt(Instant.parse("2026-08-23T00:00:00Z")); update.setIsPublic(false);

        service.update(7L, update);
        assertThat(article.getTitle()).isEqualTo("Updated"); assertThat(article.getExcerpt()).isEqualTo("Excerpt");
        assertThat(article.getSourceName()).isEqualTo("Updated source"); assertThat(article.getSourceUrl()).isEqualTo("https://example.test/updated");
        assertThat(article.getPublishedAt()).isEqualTo(Instant.parse("2026-08-23T00:00:00Z")); assertThat(article.getIsPublic()).isFalse();

        UpdateLabNewsArticleRequest clearExcerpt = new UpdateLabNewsArticleRequest(); clearExcerpt.setExcerpt(null);
        service.update(7L, clearExcerpt);
        assertThat(article.getExcerpt()).isNull();
    }

    @Test void patchRejectsInvalidRequiredFieldReplacementIncludingExplicitUrlNull() {
        LabNewsArticleEntity article = article(7L, "2026-08-20T00:00:00Z", "2026-08-01T00:00:00Z");
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article));
        UpdateLabNewsArticleRequest invalidTitle = new UpdateLabNewsArticleRequest(); invalidTitle.setTitle(" ");
        assertBadRequest(() -> service.update(7L, invalidTitle));
        UpdateLabNewsArticleRequest nullUrl = new UpdateLabNewsArticleRequest(); nullUrl.setSourceUrl(null);
        assertBadRequest(() -> service.update(7L, nullUrl));
        UpdateLabNewsArticleRequest invalidUrl = new UpdateLabNewsArticleRequest(); invalidUrl.setSourceUrl("mailto:news@example.test");
        assertBadRequest(() -> service.update(7L, invalidUrl));
        UpdateLabNewsArticleRequest nullPublishedAt = new UpdateLabNewsArticleRequest(); nullPublishedAt.setPublishedAt(null);
        assertBadRequest(() -> service.update(7L, nullPublishedAt));
    }

    @Test void deleteSoftDeletesActiveArticle() {
        LabNewsArticleEntity article = article(7L, "2026-08-20T00:00:00Z", "2026-08-01T00:00:00Z");
        when(articleRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(article));
        when(articleRepository.saveAndFlush(article)).thenReturn(article);
        service.delete(7L);
        assertThat(article.getDeletedAt()).isNotNull(); verify(articleRepository).saveAndFlush(article);
    }

    private static CreateLabNewsArticleRequest request(String sourceUrl) {
        CreateLabNewsArticleRequest request = new CreateLabNewsArticleRequest();
        request.setTitle("Title"); request.setSourceName("Source"); request.setSourceUrl(sourceUrl);
        request.setPublishedAt(Instant.parse("2026-08-20T00:00:00Z")); return request;
    }

    private static LabNewsArticleEntity article(Long id, String publishedAt, String createdAt) {
        LabNewsArticleEntity article = LabNewsArticleEntity.create("Title " + id, "Excerpt", "Source", "https://example.test/" + id,
                Instant.parse(publishedAt), true, Instant.parse(createdAt));
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
