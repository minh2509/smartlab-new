package com.smartlab.repo;

import com.smartlab.entity.EventEntity;
import com.smartlab.enums.EventStatus;
import com.smartlab.enums.EventVisibility;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventRepositoryContractTest {

    @Test
    void extendsJpaRepositoryForEventEntities() {
        Type repositoryType = EventRepository.class.getGenericInterfaces()[0];
        assertThat(repositoryType).isInstanceOf(ParameterizedType.class);
        ParameterizedType type = (ParameterizedType) repositoryType;
        assertThat(type.getRawType()).isEqualTo(JpaRepository.class);
        assertThat(type.getActualTypeArguments()).containsExactly(EventEntity.class, Long.class);
    }

    @Test
    void listExcludesDeletedRowsSupportsFiltersAndOrdersUpcomingFirstDeterministically()
            throws NoSuchMethodException {
        Method method = EventRepository.class.getMethod(
                "findActiveEvents", Long.class, EventStatus.class, Boolean.class, Instant.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("e.deletedAt is null");
        assertThat(query).contains(":projectId is null", "e.projectId = :projectId");
        assertThat(query).contains(":status is null", "e.status = :status");
        assertThat(query).contains(":upcoming = true", "e.startAt >= :now");
        assertThat(query).contains(":upcoming = false", "e.startAt < :now");
        assertThat(query).contains("case when e.startAt >= :now then 0 else 1 end");
        assertThat(query).contains("e.startAt", "e.id");
    }

    @Test
    void publicListIsConstrainedByVisibilityAndNeverAcceptsAProjectFilter()
            throws NoSuchMethodException {
        Method method = EventRepository.class.getMethod(
                "findPublicEvents",
                EventVisibility.class,
                EventStatus.class,
                Boolean.class,
                Instant.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("e.deletedAt is null", "e.visibility = :visibility");
        assertThat(query).doesNotContain("projectId");
        assertThat(query).contains(":status is null", ":upcoming = true", ":upcoming = false");
    }

    @Test
    void readsAndWritesOnlyActiveRowsAndMutationLookupUsesPessimisticLock()
            throws NoSuchMethodException {
        Method read = EventRepository.class.getMethod("findActiveById", Long.class);
        Method write = EventRepository.class.getMethod("findActiveByIdForUpdate", Long.class);

        assertThat(read.getAnnotation(Query.class).value())
                .contains("e.id = :id", "e.deletedAt is null");
        assertThat(write.getAnnotation(Query.class).value())
                .contains("e.id = :id", "e.deletedAt is null");
        assertThat(write.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(EventRepository.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("delete"));
    }
}
