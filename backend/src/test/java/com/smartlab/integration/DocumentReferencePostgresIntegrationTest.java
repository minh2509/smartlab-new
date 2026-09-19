package com.smartlab.integration;

import com.smartlab.repo.DocumentRepository;
import com.smartlab.repo.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=b2-p0-02-postgres-integration-secret",
        "smartlab.email-outbox.max-per-poll=0"
})
@Transactional
class DocumentReferencePostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @BeforeEach
    void requireExactIsolatedDatabase() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(currentUser()).isEqualTo("smartlab_user");
    }

    @Test
    void ownCurrentAndRetainedVersionReferencesAreExcludedEvenWhenTheFileRepeats() {
        Fixture fixture = fixture("own-repeat");
        long documentId = insertDocument(fixture.projectId(), fixture.fileId(), fixture.userId(), "own-document");
        insertVersion(documentId, fixture.fileId(), fixture.userId(), 1);
        insertVersion(documentId, fixture.fileId(), fixture.userId(), 2);

        assertThat(documentRepository.existsActiveCurrentReferenceOutsideDocument(fixture.fileId(), documentId))
                .isFalse();
        assertThat(documentVersionRepository.existsActiveReferenceOutsideDocument(fixture.fileId(), documentId))
                .isFalse();
    }

    @Test
    void activeOtherDocumentCurrentReferenceIsExternal() {
        Fixture fixture = fixture("other-current");
        long deletingDocumentId = insertDocument(
                fixture.projectId(), fixture.fileId(), fixture.userId(), "deleting-document");
        insertDocument(fixture.projectId(), fixture.fileId(), fixture.userId(), "other-current-document");

        assertThat(documentRepository.existsActiveCurrentReferenceOutsideDocument(
                fixture.fileId(), deletingDocumentId)).isTrue();
    }

    @Test
    void activeOtherDocumentRetainedVersionReferenceIsExternal() {
        Fixture fixture = fixture("other-version");
        long deletingDocumentId = insertDocument(
                fixture.projectId(), fixture.fileId(), fixture.userId(), "deleting-document");
        long otherDocumentId = insertDocument(
                fixture.projectId(), fixture.otherFileId(), fixture.userId(), "other-version-document");
        insertVersion(otherDocumentId, fixture.fileId(), fixture.userId(), 1);

        assertThat(documentVersionRepository.existsActiveReferenceOutsideDocument(
                fixture.fileId(), deletingDocumentId)).isTrue();
    }

    @Test
    void softDeletedOtherDocumentDoesNotRemainAnActiveExternalReference() {
        Fixture fixture = fixture("deleted-other");
        long deletingDocumentId = insertDocument(
                fixture.projectId(), fixture.fileId(), fixture.userId(), "deleting-document");
        long otherDocumentId = insertDocument(
                fixture.projectId(), fixture.fileId(), fixture.userId(), "deleted-other-document");
        insertVersion(otherDocumentId, fixture.fileId(), fixture.userId(), 1);
        jdbc.update("update documents set deleted_at = ? where id = ?", Timestamp.from(java.time.Instant.now()), otherDocumentId);

        assertThat(documentRepository.existsActiveCurrentReferenceOutsideDocument(
                fixture.fileId(), deletingDocumentId)).isFalse();
        assertThat(documentVersionRepository.existsActiveReferenceOutsideDocument(
                fixture.fileId(), deletingDocumentId)).isFalse();
    }

    private Fixture fixture(String label) {
        String marker = "b2-p0-02-it-" + label + "-" + UUID.randomUUID();
        long userId = insertUser(marker);
        long projectId = insertProject(marker, userId);
        long fileId = insertFile(marker + "-file", userId, projectId);
        long otherFileId = insertFile(marker + "-other-file", userId, projectId);
        return new Fixture(userId, projectId, fileId, otherFileId);
    }

    private long insertUser(String marker) {
        return jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, UUID.randomUUID().toString(), marker, marker + "@example.test", "test-password");
    }

    private long insertProject(String marker, long userId) {
        String code = "b2p002-" + UUID.randomUUID();
        return jdbc.queryForObject("""
                insert into projects (
                    code, name, project_type, leader_user_id, status, is_public,
                    is_featured, created_by_user_id
                ) values (?, ?, 'RESEARCH', ?, 'IN_PROGRESS', false, false, ?)
                returning id
                """, Long.class, code, marker, userId, userId);
    }

    private long insertFile(String storageKey, long userId, long projectId) {
        return jdbc.queryForObject("""
                insert into files (
                    owner_user_id, project_id, storage_provider, storage_key,
                    original_name, mime_type, size_bytes, access_scope
                ) values (?, ?, 'TEST', ?, ?, 'text/plain', 1, 'PUBLIC')
                returning id
                """, Long.class, userId, projectId, storageKey, storageKey + ".txt");
    }

    private long insertDocument(long projectId, long fileId, long userId, String title) {
        return jdbc.queryForObject("""
                insert into documents (project_id, title, current_file_id, created_by_user_id)
                values (?, ?, ?, ?)
                returning id
                """, Long.class, projectId, title, fileId, userId);
    }

    private void insertVersion(long documentId, long fileId, long userId, int versionNo) {
        jdbc.update("""
                insert into document_versions (document_id, file_id, version_no, uploaded_by_user_id)
                values (?, ?, ?, ?)
                """, documentId, fileId, versionNo, userId);
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }

    private record Fixture(long userId, long projectId, long fileId, long otherFileId) {
    }
}
