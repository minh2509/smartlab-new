package com.smartlab.service.impl;

import com.smartlab.entity.PostEntity;
import com.smartlab.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostCreateAttemptService {

    private final PostRepository postRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PostEntity persist(PostEntity post) {
        try {
            return postRepository.saveAndFlush(post);
        } catch (DataIntegrityViolationException exception) {
            if (PostSlugConstraintClassifier.isSlugCollision(exception)) {
                throw new PostSlugCollisionException(exception);
            }
            throw exception;
        }
    }
}

final class PostSlugCollisionException extends RuntimeException {

    PostSlugCollisionException(DataIntegrityViolationException cause) {
        super(cause);
    }
}
