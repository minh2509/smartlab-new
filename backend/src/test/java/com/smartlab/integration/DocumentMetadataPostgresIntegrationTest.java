package com.smartlab.integration;

import com.smartlab.dto.request.UpdateDocumentRequest;
import com.smartlab.dto.response.DocumentResponse;
import com.smartlab.service.AuditService;
import com.smartlab.service.DocumentService;
import com.smartlab.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=b2-p0-03-postgres-integration-secret",
        "smartlab.email-outbox.max-per-poll=0"
})
class DocumentMetadataPostgresIntegrationTest {
    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private FileStorage fileStorage;

    private TransactionTemplate transactionTemplate;
    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> projectIds = new LinkedHashSet<>();
    private final Set<Long> documentIds = new LinkedHashSet<>();
    private final Set<String> storageKeys = new LinkedHashSet<>();

    @BeforeEach
    void requireExactIsolatedDatabaseAndPrepareTransactionTemplate() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(currentUser()).isEqualTo("smartlab_user");
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void removeOnlyThisTestFixtures() {
        for (Long documentId : documentIds) {
            jdbc.update("delete from document_versions where document_id = ?", documentId);
            jdbc.update("delete from documents where id = ?", documentId);
        }
        for (String storageKey : storageKeys) {
            jdbc.update("delete from files where storage_key = ?", storageKey);
        }
        for (Long projectId : projectIds) {
            jdbc.update("delete from project_members where project_id = ?", projectId);
            jdbc.update("delete from projects where id = ?", projectId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }
        reset(auditService, fileStorage);
        storageKeys.clear();
        documentIds.clear();
        projectIds.clear();
        userIds.clear();
    }

    @Test
    void metadataPersistsWithoutChangingCurrentFileOrVersionRows() {
        Fixture fixture = fixture("success", true);
        DocumentResponse response = documentService.update(
                fixture.documentId(), request("  Updated title  ", "  Updated description  "), auth(fixture.email()));

        assertThat(response.getTitle()).isEqualTo("Updated title");
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getCurrentFile().getId()).isEqualTo(fixture.currentFileId());
        assertThat(jdbc.queryForMap(
                "select title, description, current_file_id from documents where id = ?", fixture.documentId()))
                .containsEntry("title", "Updated title")
                .containsEntry("description", "Updated description")
                .containsEntry("current_file_id", fixture.currentFileId());
        assertThat(jdbc.queryForObject(
                "select count(*) from document_versions where document_id = ?", Long.class, fixture.documentId()))
                .isEqualTo(2L);
        assertThat(jdbc.queryForList(
                "select file_id from document_versions where document_id = ? order by version_no", Long.class, fixture.documentId()))
                .containsExactly(fixture.retainedFileId(), fixture.currentFileId());
    }

    @Test
    void listReturnsEmptyForAnExistingProjectWithoutDocuments() {
        UserFixture owner = insertUser("list-empty-owner");
        assignMemberRole(owner.id());
        long projectId = insertProject("list-empty", owner.id());
        jdbc.update("insert into project_members (project_id, user_id, project_role, status) values (?, ?, 'MEMBER', 'ACTIVE')",
                projectId, owner.id());

        assertThat(documentService.list(projectId, auth(owner.email()))).isEmpty();
    }

    @Test
    void listLoadsDocumentsAndBatchVersionNumbersFromPostgres() {
        Fixture fixture = fixture("list-populated", true);
        assignMemberRole(fixture.ownerId());

        assertThat(documentService.list(fixture.projectId(), auth(fixture.email())))
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.getId()).isEqualTo(fixture.documentId());
                    assertThat(document.getCurrentVersionNo()).isEqualTo(2);
                    assertThat(document.getDescription()).isEqualTo("Original description");
                });
    }

    @Test
    void unauthorizedManagerCannotMutatePersistedMetadata() {
        Fixture fixture = fixture("forbidden", true);
        UserFixture other = insertUser("ordinary-member");
        jdbc.update("insert into project_members (project_id, user_id, project_role, status) values (?, ?, 'MEMBER', 'ACTIVE')",
                fixture.projectId(), other.id());

        assertThatThrownBy(() -> documentService.update(
                fixture.documentId(), request("Should not persist", "Should not persist"), auth(other.email())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(403));

        assertThat(jdbc.queryForMap(
                "select title, description, current_file_id from documents where id = ?", fixture.documentId()))
                .containsEntry("title", "Original title")
                .containsEntry("description", "Original description")
                .containsEntry("current_file_id", fixture.currentFileId());
    }

    @Test
    void outerRollbackRestoresPreviousMetadata() {
        Fixture fixture = fixture("rollback", true);

        transactionTemplate.executeWithoutResult(status -> {
            documentService.update(
                    fixture.documentId(), request("Rolled back title", "Rolled back description"), auth(fixture.email()));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForMap(
                "select title, description, current_file_id from documents where id = ?", fixture.documentId()))
                .containsEntry("title", "Original title")
                .containsEntry("description", "Original description")
                .containsEntry("current_file_id", fixture.currentFileId());
    }

    private Fixture fixture(String label, boolean leader) {
        UserFixture owner = insertUser(label + "-owner");
        long projectId = insertProject(label, owner.id());
        jdbc.update("insert into project_members (project_id, user_id, project_role, status) values (?, ?, ?, 'ACTIVE')",
                projectId, owner.id(), leader ? "LEADER" : "MEMBER");
        long currentFileId = insertFile(registerStorageKey(label + "-current"), owner.id(), projectId);
        long retainedFileId = insertFile(registerStorageKey(label + "-retained"), owner.id(), projectId);
        long documentId = jdbc.queryForObject("""
                insert into documents (project_id, title, description, current_file_id, created_by_user_id)
                values (?, 'Original title', 'Original description', ?, ?)
                returning id
                """, Long.class, projectId, currentFileId, owner.id());
        documentIds.add(documentId);
        jdbc.update("""
                insert into document_versions (document_id, file_id, version_no, uploaded_by_user_id)
                values (?, ?, 1, ?), (?, ?, 2, ?)
                """, documentId, retainedFileId, owner.id(), documentId, currentFileId, owner.id());
        return new Fixture(owner.id(), owner.email(), projectId, documentId, currentFileId, retainedFileId);
    }

    private void assignMemberRole(long userId) {
        int rows = jdbc.update("""
                insert into user_roles (user_id, role_id, assigned_by)
                select ?, id, 'document-list-integration' from roles where code = 'MEMBER'
                """, userId);
        assertThat(rows).isEqualTo(1);
    }

    private UserFixture insertUser(String label) {
        String marker = "p0-03-" + label + "-" + UUID.randomUUID();
        long id = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, UUID.randomUUID().toString(), marker, marker + "@example.test", "test-password");
        userIds.add(id);
        return new UserFixture(id, marker + "@example.test");
    }

    private long insertProject(String label, long leaderId) {
        String code = "P003-" + UUID.randomUUID().toString().substring(0, 20);
        long id = jdbc.queryForObject("""
                insert into projects (code, name, project_type, leader_user_id, status, is_public, is_featured, created_by_user_id)
                values (?, ?, 'RESEARCH', ?, 'IN_PROGRESS', true, false, ?)
                returning id
                """, Long.class, code, "P0-03 " + label, leaderId, leaderId);
        projectIds.add(id);
        return id;
    }

    private long insertFile(String storageKey, long ownerId, long projectId) {
        return jdbc.queryForObject("""
                insert into files (owner_user_id, project_id, storage_provider, storage_key, original_name, mime_type, size_bytes, access_scope)
                values (?, ?, 'TEST', ?, ?, 'text/plain', 1, 'PUBLIC')
                returning id
                """, Long.class, ownerId, projectId, storageKey, storageKey + ".txt");
    }

    private String registerStorageKey(String label) {
        String key = "p0-03-" + label + "-" + UUID.randomUUID();
        storageKeys.add(key);
        return key;
    }

    private static UpdateDocumentRequest request(String title, String description) {
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setTitle(title);
        request.setDescription(description);
        return request;
    }

    private static Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, java.util.List.of());
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }

    private record UserFixture(long id, String email) {
    }

    private record Fixture(long ownerId, String email, long projectId, long documentId, long currentFileId, long retainedFileId) {
    }
}
