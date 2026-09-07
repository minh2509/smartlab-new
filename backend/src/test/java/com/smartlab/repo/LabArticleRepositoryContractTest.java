package com.smartlab.repo;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LabArticleRepositoryContractTest {
    @Test void publicQueriesFilterOnlyActivePublishedArticlesAndOrderInDatabase() throws Exception {
        for (String name : new String[]{"findNewestPublished", "findPublishedArchive"}) {
            Method method = LabArticleRepository.class.getMethod(name, org.springframework.data.domain.Pageable.class);
            String query = method.getAnnotation(Query.class).value();
            assertThat(query).contains("article.deletedAt is null", "LabArticleStatus.PUBLISHED",
                    "order by article.publishedAt desc, article.createdAt desc, article.id desc");
        }
    }

    @Test void adminQueryExcludesOnlySoftDeletedArticlesAndOrdersForManagement() throws Exception {
        Method method = LabArticleRepository.class.getMethod("findActiveForAdmin", org.springframework.data.domain.Pageable.class);
        assertThat(method.getAnnotation(Query.class).value()).contains("article.deletedAt is null",
                "order by article.updatedAt desc, article.createdAt desc, article.id desc").doesNotContain("PUBLISHED");
    }

    @Test void articleRepositoryHasNoPostDomainDependency() {
        assertThat(LabArticleRepository.class.getDeclaredMethods()).noneMatch(method -> method.toString().contains("Post"));
    }
}
