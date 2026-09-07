package com.smartlab.repo;

import com.smartlab.enums.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRepositoryContractTest {

    @Test
    void publicRecruitingQueryExcludesDeletedAndNonEligibleProjects() throws NoSuchMethodException {
        Method method = ProjectRepository.class.getMethod(
                "findPublicRecruitingProjects", java.util.List.class,
                org.springframework.data.domain.Pageable.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains(
                "p.deletedAt is null",
                "p.isPublic = true",
                "coalesce(p.isRecruiting, false) = true",
                "p.status in :statuses"
        );
        assertThat(ProjectStatus.values()).containsExactly(
                ProjectStatus.PROPOSED,
                ProjectStatus.PREPARING,
                ProjectStatus.IN_PROGRESS,
                ProjectStatus.PAUSED,
                ProjectStatus.COMPLETED,
                ProjectStatus.CLOSED
        );
    }

    @Test
    void publicArchiveQueryContainsServerSideSearchFiltersAndResearchFieldPredicates()
            throws NoSuchMethodException {
        Method method = ProjectRepository.class.getMethod(
                "findPublicProjects",
                String.class,
                com.smartlab.enums.ProjectType.class,
                java.util.List.class,
                boolean.class,
                boolean.class,
                java.util.List.class,
                Long.class,
                String.class,
                org.springframework.data.domain.Pageable.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains(
                "p.isPublic = true",
                ":query = ''",
                ":projectType is null",
                "p.status in :statuses",
                ":recruitingOnly = false",
                "coalesce(p.isRecruiting, false) = true",
                ":excludeEffectiveRecruiting = false",
                ":recruitableStatuses",
                ":researchFieldId is null",
                ":researchFieldCode = ''",
                "order by p.createdAt desc, p.id desc"
        );
        assertThat(query).doesNotContain(":query is null", ":researchFieldCode is null");
    }

    @Test
    void publicDetailQueryRequiresActivePublicProject() throws NoSuchMethodException {
        Method method = ProjectRepository.class.getMethod("findPublicById", Long.class);
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains(
                "p.id = :id",
                "p.deletedAt is null",
                "p.isPublic = true"
        );
    }
}
