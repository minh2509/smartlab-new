package com.smartlab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PostSlugConstraintClassifierTest {

    @Test
    void classifiesExactPostSlugUniqueViolation() {
        DataIntegrityViolationException exception = violation("23505", "posts_slug_key");

        assertThat(PostSlugConstraintClassifier.isSlugCollision(exception)).isTrue();
    }

    @Test
    void doesNotClassifyAnotherUniqueConstraint() {
        DataIntegrityViolationException exception = violation("23505", "posts_title_key");

        assertThat(PostSlugConstraintClassifier.isSlugCollision(exception)).isFalse();
    }

    @Test
    void doesNotClassifySlugConstraintWithNonUniqueSqlState() {
        DataIntegrityViolationException exception = violation("23503", "posts_slug_key");

        assertThat(PostSlugConstraintClassifier.isSlugCollision(exception)).isFalse();
    }

    @Test
    void doesNotClassifyForeignKeyViolation() {
        DataIntegrityViolationException exception = violation("23503", "posts_author_user_id_fkey");

        assertThat(PostSlugConstraintClassifier.isSlugCollision(exception)).isFalse();
    }

    @Test
    void doesNotClassifyUnstructuredDataIntegrityViolation() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate-like text without structured evidence");

        assertThat(PostSlugConstraintClassifier.isSlugCollision(exception)).isFalse();
    }

    private DataIntegrityViolationException violation(String sqlState, String constraintName) {
        SQLException sqlException = new SQLException("structured PostgreSQL failure", sqlState);
        ConstraintViolationException hibernateException =
                new ConstraintViolationException("constraint violation", sqlException, constraintName);
        return new DataIntegrityViolationException("persistence failed", hibernateException);
    }
}
