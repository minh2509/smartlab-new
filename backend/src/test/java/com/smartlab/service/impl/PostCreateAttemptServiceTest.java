package com.smartlab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import com.smartlab.entity.PostEntity;
import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.PostRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class PostCreateAttemptServiceTest {

    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostCreateAttemptService service = new PostCreateAttemptService(postRepository);

    @Test
    void persistenceAttemptUsesRequiresNewTransaction() throws Exception {
        Transactional transactional = PostCreateAttemptService.class
                .getMethod("persist", PostEntity.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void forcesConstraintEvaluationWithSaveAndFlush() {
        PostEntity post = draft("r14-post");
        when(postRepository.saveAndFlush(post)).thenReturn(post);

        assertThat(service.persist(post)).isSameAs(post);

        verify(postRepository).saveAndFlush(post);
    }

    @Test
    void translatesOnlyExactSlugCollisionForOuterRetry() {
        PostEntity post = draft("r14-post");
        DataIntegrityViolationException collision = violation("23505", "posts_slug_key");
        when(postRepository.saveAndFlush(post)).thenThrow(collision);

        assertThatThrownBy(() -> service.persist(post))
                .isInstanceOf(PostSlugCollisionException.class)
                .hasCause(collision);
    }

    @Test
    void propagatesNonSlugIntegrityViolationWithoutTranslation() {
        PostEntity post = draft("r14-post");
        DataIntegrityViolationException foreignKeyViolation =
                violation("23503", "posts_author_user_id_fkey");
        when(postRepository.saveAndFlush(post)).thenThrow(foreignKeyViolation);

        assertThatThrownBy(() -> service.persist(post)).isSameAs(foreignKeyViolation);
    }

    private PostEntity draft(String slug) {
        return PostEntity.createDraft(
                17L,
                "R14 title",
                slug,
                null,
                Map.of(),
                PostVisibility.LAB,
                null,
                Instant.parse("2026-08-09T00:00:00Z")
        );
    }

    private DataIntegrityViolationException violation(String sqlState, String constraintName) {
        SQLException sqlException = new SQLException("structured PostgreSQL failure", sqlState);
        ConstraintViolationException hibernateException =
                new ConstraintViolationException("constraint violation", sqlException, constraintName);
        return new DataIntegrityViolationException("persistence failed", hibernateException);
    }
}
