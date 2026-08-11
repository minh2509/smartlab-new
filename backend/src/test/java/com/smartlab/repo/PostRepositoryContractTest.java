package com.smartlab.repo;

import com.smartlab.entity.PostEntity;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryContractTest {

    @Test
    void extendsJpaRepositoryForPostEntities() {
        Type repositoryType = PostRepository.class.getGenericInterfaces()[0];

        assertThat(repositoryType).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) repositoryType;
        assertThat(parameterizedType.getRawType()).isEqualTo(JpaRepository.class);
        assertThat(parameterizedType.getActualTypeArguments()).containsExactly(PostEntity.class, Long.class);
    }

    @Test
    void activeListQueryIncludesOwnerPublicLabOrActiveProjectMembershipAndDeterministicOrdering()
            throws NoSuchMethodException {
        String query = queryFor("findActiveReadableByViewerUserId", Long.class, java.util.List.class);

        assertThat(query).contains("p.deletedAt is null");
        assertThat(query).contains("p.authorUserId = :viewerUserId");
        assertThat(query).contains("PostStatus.PUBLISHED");
        assertThat(query).contains("PostVisibility.PUBLIC", "PostVisibility.LAB");
        assertThat(query).contains("PostVisibility.PROJECT", "p.projectId is not null", "p.projectId in :activeProjectIds");
        assertThat(query).contains("order by p.createdAt desc, p.id desc");
    }

    @Test
    void activeSlugLookupExcludesDeletedPosts() throws NoSuchMethodException {
        String query = queryFor("findActiveBySlug", String.class);

        assertThat(query).contains("p.slug = :slug");
        assertThat(query).contains("p.deletedAt is null");
    }

    @Test
    void reviewerListQueryReturnsOnlyActiveNonSelfPendingPostsInDeterministicOrder()
            throws NoSuchMethodException {
        Method method = PostRepository.class.getMethod(
                "findActivePendingReviewableByReviewerUserId",
                Long.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("p.deletedAt is null");
        assertThat(query).contains("PostStatus.PENDING_REVIEW");
        assertThat(query).contains("p.authorUserId is null", "p.authorUserId <> :reviewerUserId");
        assertThat(query).contains("order by p.createdAt desc, p.id desc");
        assertThat(query).doesNotContain("PostVisibility");
        assertThat(method.isAnnotationPresent(Lock.class)).isFalse();
        assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
    }

    @Test
    void reviewerDetailQueryConcealsEveryPostOutsideTheSameReviewabilityPredicate()
            throws NoSuchMethodException {
        Method method = PostRepository.class.getMethod(
                "findActivePendingReviewableByIdAndReviewerUserId",
                Long.class,
                Long.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("p.id = :id");
        assertThat(query).contains("p.deletedAt is null");
        assertThat(query).contains("PostStatus.PENDING_REVIEW");
        assertThat(query).contains("p.authorUserId is null", "p.authorUserId <> :reviewerUserId");
        assertThat(query).doesNotContain("PostVisibility");
        assertThat(method.isAnnotationPresent(Lock.class)).isFalse();
        assertThat(method.isAnnotationPresent(Modifying.class)).isFalse();
    }

    @Test
    void ownershipAndStateEligibilitySeamExistsForFuturePatchAndDelete() throws NoSuchMethodException {
        String activeByIdQuery = queryFor("findActiveById", Long.class);
        String query = queryFor("findOwnedActiveByIdAndStatus", Long.class, Long.class, com.smartlab.enums.PostStatus.class);

        assertThat(activeByIdQuery).contains("p.id = :id", "p.deletedAt is null");
        assertThat(query).contains("p.id = :id");
        assertThat(query).contains("p.authorUserId = :authorUserId");
        assertThat(query).contains("p.deletedAt is null");
        assertThat(query).contains("p.status = :status");
    }

    @Test
    void patchLookupUsesPessimisticWriteLockAndExcludesDeletedPosts() throws NoSuchMethodException {
        Method method = PostRepository.class.getMethod("findActiveByIdForUpdate", Long.class);
        Lock lock = method.getAnnotation(Lock.class);
        String query = method.getAnnotation(Query.class).value();

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(query).contains("p.id = :id", "p.deletedAt is null");
    }

    @Test
    void softDeleteUsesAConditionalExpectedStateUpdateInsteadOfPhysicalDelete() throws NoSuchMethodException {
        Method method = PostRepository.class.getMethod(
                "softDeleteOwnedDraft",
                Long.class,
                Long.class,
                java.time.Instant.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(method.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(query).contains("p.deletedAt = :mutationInstant", "p.updatedAt = :mutationInstant");
        assertThat(query).contains("p.id = :id", "p.authorUserId = :authorUserId");
        assertThat(query).contains("p.deletedAt is null", "PostStatus.DRAFT");
        assertThat(query).doesNotContain("delete from");
    }

    private static String queryFor(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return PostRepository.class.getMethod(name, parameterTypes).getAnnotation(Query.class).value();
    }
}
