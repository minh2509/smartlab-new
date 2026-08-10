package com.smartlab.repo;

import com.smartlab.entity.PostReviewEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PostReviewRepositoryContractTest {

    @Test
    void extendsJpaRepositoryWithTheCanonicalReviewEntityAndIdType() {
        Type repositoryType = PostReviewRepository.class.getGenericInterfaces()[0];

        assertThat(repositoryType).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) repositoryType;
        assertThat(parameterizedType.getRawType()).isEqualTo(JpaRepository.class);
        assertThat(parameterizedType.getActualTypeArguments()).containsExactly(PostReviewEntity.class, Long.class);
        assertThat(PostReviewRepository.class.isAnnotationPresent(Repository.class)).isTrue();
    }

    @Test
    void exposesReliableJpaPersistenceOperationsWithoutCustomBusinessMutationMethods() {
        assertThat(Arrays.stream(PostReviewRepository.class.getMethods())
                .map(method -> method.getName()))
                .contains("save", "saveAndFlush", "flush");
        assertThat(PostReviewRepository.class.getDeclaredMethods()).isEmpty();
    }
}
