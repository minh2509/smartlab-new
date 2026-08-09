package com.smartlab.service.impl;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class PostSlugConstraintClassifier {

    static final String POSTS_SLUG_UNIQUE_CONSTRAINT = "posts_slug_key";

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private PostSlugConstraintClassifier() {
    }

    static boolean isSlugCollision(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                SQLException sqlException = constraintViolation.getSQLException();
                return POSTS_SLUG_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName())
                        && sqlException != null
                        && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
