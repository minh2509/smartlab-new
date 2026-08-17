package com.smartlab.repo;

import com.smartlab.entity.PostReviewEntity;
import com.smartlab.enums.ReviewDecision;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;

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
    void exposesInheritedPersistenceOperationsAndOnlyTheApprovedCustomReadFinder() {
        assertThat(Arrays.stream(PostReviewRepository.class.getMethods())
                .map(method -> method.getName()))
                .contains("save", "saveAndFlush", "flush");

        Method[] declaredMethods = PostReviewRepository.class.getDeclaredMethods();
        assertThat(declaredMethods).hasSize(1);

        Method finder = declaredMethods[0];
        assertThat(finder.getName()).isEqualTo("findFirstByPostIdAndDecisionOrderByCreatedAtDescIdDesc");
        assertThat(finder.getParameterTypes()).containsExactly(Long.class, ReviewDecision.class);
        assertThat(finder.getReturnType()).isEqualTo(Optional.class);

        assertThat(Arrays.stream(declaredMethods)
                .map(Method::getName)
                .map(String::toLowerCase))
                .noneMatch(name -> name.startsWith("save")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("update"));
    }
}
