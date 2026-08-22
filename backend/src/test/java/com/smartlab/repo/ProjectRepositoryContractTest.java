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
                "p.isRecruiting = true",
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
}
