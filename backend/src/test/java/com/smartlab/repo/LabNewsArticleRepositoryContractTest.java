package com.smartlab.repo;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LabNewsArticleRepositoryContractTest {
    @Test void publicReadFiltersActivePublicRecordsAndOrdersWithDeterministicDatabaseFallbacks() throws Exception {
        Method method = LabNewsArticleRepository.class.getMethod("findNewestPublic", org.springframework.data.domain.Pageable.class);
        String query = method.getAnnotation(Query.class).value();
        assertThat(query).contains("article.deletedAt is null", "article.isPublic = true",
                "order by article.publishedAt desc, article.createdAt desc, article.id desc");
    }

    @Test void adminReadFiltersOnlySoftDeletedRecordsAndOrdersForDatabasePaging() throws Exception {
        Method method = LabNewsArticleRepository.class.getMethod("findActiveForAdmin", org.springframework.data.domain.Pageable.class);
        String query = method.getAnnotation(Query.class).value();
        assertThat(query).contains("article.deletedAt is null", "order by article.publishedAt desc, article.createdAt desc, article.id desc")
                .doesNotContain("article.isPublic = true");
    }

    @Test void publicArchiveFiltersActivePublicRecordsOrdersInTheDatabaseAndUsesPageable() throws Exception {
        Method method = LabNewsArticleRepository.class.getMethod("findPublicArchive", org.springframework.data.domain.Pageable.class);
        String query = method.getAnnotation(Query.class).value();
        assertThat(method.getReturnType()).isEqualTo(org.springframework.data.domain.Page.class);
        assertThat(query).contains("article.deletedAt is null", "article.isPublic = true",
                "order by article.publishedAt desc, article.id desc");
    }

    @Test void publicSourcesAndYearsOnlyReadPublicActiveRows() throws Exception {
        Method sources = LabNewsArticleRepository.class.getMethod("findPublicSources");
        Method years = LabNewsArticleRepository.class.getMethod("findPublicYears");
        assertThat(sources.getAnnotation(Query.class).value()).contains("article.deletedAt is null", "article.isPublic = true", "trim(article.sourceName)");
        assertThat(years.getAnnotation(Query.class).value()).contains("article.deletedAt is null", "article.isPublic = true", "year(article.publishedAt)");
    }
}
