package com.smartlab.repo;

import com.smartlab.enums.AchievementType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LabAchievementRepositoryContractTest {
    @Test void adminQueryUsesNonNullTypedSearchParameterAndFiltersPaginatesInDatabase() throws Exception {
        Method method = LabAchievementRepository.class.getMethod("findActiveForAdmin", Integer.class, AchievementType.class,
                Boolean.class, String.class, Pageable.class);
        String query = method.getAnnotation(Query.class).value();
        assertThat(query).contains("a.deletedAt is null", ":year is null or a.achievementYear = :year",
                ":type is null or a.achievementType = :type", ":isPublic is null or a.isPublic = :isPublic",
                ":query = ''", "lower(a.title) like concat('%', lower(:query), '%')",
                "lower(a.summary) like concat('%', lower(:query), '%')",
                "order by a.achievementYear desc, a.achievementDate desc nulls last, a.updatedAt desc, a.id desc")
                .doesNotContain("a.isPublic = true", ":query is null");
        assertThat(method.getParameterTypes()).containsExactly(Integer.class, AchievementType.class, Boolean.class, String.class,
                Pageable.class);
    }

    @Test void attachmentQueriesKeepActiveOrderingAndFileDeleteProtectionInDatabase() throws Exception {
        Method activeList = LabAchievementFileRepository.class.getMethod("findActiveByAchievementId", Long.class);
        Method publicDownload = LabAchievementFileRepository.class.getMethod("findPublicActiveByIdAndAchievementId", Long.class, Long.class);
        Method protection = LabAchievementFileRepository.class.getMethod("existsActiveReferenceForActiveAchievement", Long.class);
        assertThat(activeList.getAnnotation(Query.class).value()).contains("join fetch af.file", "af.deletedAt is null", "af.file.deletedAt is null", "order by af.sortOrder asc, af.id asc");
        assertThat(publicDownload.getAnnotation(Query.class).value()).contains("join fetch af.file", "af.deletedAt is null", "af.file.deletedAt is null", "af.file.accessScope = 'PUBLIC'");
        assertThat(protection.getAnnotation(Query.class).value()).contains("af.file.id = :fileId", "af.deletedAt is null", "af.achievement.deletedAt is null");
    }
}
