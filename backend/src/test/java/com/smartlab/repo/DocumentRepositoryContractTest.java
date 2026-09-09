package com.smartlab.repo;

import com.smartlab.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    @Test
    void publicArchiveUsesSpecificationWithPublicVisibilityAndEntityGraph()
            throws NoSuchMethodException {
        Type specificationType = DocumentRepository.class.getGenericInterfaces()[1];
        assertThat(specificationType).isInstanceOf(ParameterizedType.class);
        assertThat(((ParameterizedType) specificationType).getRawType()).isEqualTo(JpaSpecificationExecutor.class);
        assertThat(((ParameterizedType) specificationType).getActualTypeArguments()).containsExactly(DocumentEntity.class);

        Method findAll = DocumentRepository.class.getMethod("findAll", Specification.class, Pageable.class);
        assertThat(findAll.getReturnType()).isEqualTo(Page.class);
        assertThat(findAll.getAnnotation(EntityGraph.class).attributePaths())
                .containsExactlyInAnyOrder("currentFile", "project");

        Method years = DocumentRepository.class.getMethod("findPublicYears");
        String yearsQuery = years.getAnnotation(Query.class).value();
        assertThat(yearsQuery).contains(
                "extract(year from d.updatedAt)",
                "d.deletedAt is null",
                "f.deletedAt is null",
                "f.accessScope = 'PUBLIC'",
                "p.deletedAt is null",
                "p.isPublic = true"
        );
    }
}
