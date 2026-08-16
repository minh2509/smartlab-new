package com.smartlab.repo;

import com.smartlab.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRepositoryContractTest {
    @Test
    void extendsJpaRepositoryForDocumentEntities() {
        Type repositoryType = DocumentRepository.class.getGenericInterfaces()[0];

        assertThat(repositoryType).isInstanceOf(ParameterizedType.class);
        ParameterizedType type = (ParameterizedType) repositoryType;
        assertThat(type.getRawType()).isEqualTo(JpaRepository.class);
        assertThat(type.getActualTypeArguments()).containsExactly(DocumentEntity.class, Long.class);
    }

    @Test
    void directReadsAndWritesExcludeDeletedDocumentsAndDeletedParentProjects()
            throws NoSuchMethodException {
        Method read = DocumentRepository.class.getMethod("findActiveById", Long.class);
        Method write = DocumentRepository.class.getMethod("findActiveByIdForUpdate", Long.class);
        String readQuery = read.getAnnotation(Query.class).value();
        String writeQuery = write.getAnnotation(Query.class).value();

        assertThat(readQuery).contains(
                "d.id = :id",
                "d.deletedAt is null",
                "d.project.deletedAt is null",
                "f.deletedAt is null"
        );
        assertThat(writeQuery).contains(
                "d.id = :id",
                "d.deletedAt is null",
                "d.project.deletedAt is null"
        );
        assertThat(write.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
