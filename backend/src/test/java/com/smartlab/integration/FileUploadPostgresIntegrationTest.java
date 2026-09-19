package com.smartlab.integration;

import com.smartlab.dto.response.FileResponse;
import com.smartlab.service.FileService;
import com.smartlab.storage.FileStorage;
import com.smartlab.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=b2-p0-01-postgres-integration-secret",
        "smartlab.email-outbox.max-per-poll=0",
        "smartlab.file.max-size-bytes=3"
})
class FileUploadPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";
    private static final String ASSIGNED_BY = "p0-01-integration";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FileService fileService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FileStorage fileStorage;

    private TransactionTemplate transactionTemplate;
    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> projectIds = new LinkedHashSet<>();
    private final Set<String> storageKeys = new LinkedHashSet<>();

    @BeforeEach
    void requireExactIsolatedDatabaseAndPrepareTransactionTemplate() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(currentUser()).isEqualTo("smartlab_user");
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void removeOnlyThisTestFixtures() {
        for (String storageKey : storageKeys) {
            jdbc.update("delete from files where storage_key = ?", storageKey);
        }
        for (Long projectId : projectIds) {
            jdbc.update("delete from project_members where project_id = ?", projectId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from user_roles where user_id = ?", userId);
        }
        for (Long projectId : projectIds) {
            jdbc.update("delete from projects where id = ?", projectId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from tbl_user where id = ?", userId);
        }
        storageKeys.clear();
        projectIds.clear();
        userIds.clear();
        reset(fileStorage);
    }

    @Test
    void successfulStandaloneUploadPersistsMetadataAndReturnsThePersistedResponse() {
        UserFixture owner = insertUser("standalone");
        String storageKey = registerStorageKey("standalone");
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, "https://storage.test/" + storageKey));

        FileResponse response = fileService.upload(
                textFile("notes.txt", "ok"), "PUBLIC", "integration description", owner.email());

        assertThat(response.getId()).isPositive();
        assertThat(response.getOriginalName()).isEqualTo("notes.txt");
        assertThat(response.getMimeType()).isEqualTo("text/plain");
        assertThat(response.getSizeBytes()).isEqualTo(2L);
        assertThat(response.getAccessScope()).isEqualTo("PUBLIC");
        assertThat(response.getDescription()).isEqualTo("integration description");
        assertThat(response.getCreatedAt()).isNotNull();

        var row = jdbc.queryForMap("""
                select owner_user_id, project_id, storage_provider, storage_key,
                       public_url, original_name, mime_type, size_bytes,
                       access_scope, description, created_at, deleted_at
                from files where storage_key = ?
                """, storageKey);
        assertThat(row.get("owner_user_id")).isEqualTo(owner.id());
        assertThat(row.get("project_id")).isNull();
        assertThat(row.get("storage_provider")).isEqualTo("GOOGLE_DRIVE");
        assertThat(row.get("storage_key")).isEqualTo(storageKey);
        assertThat(row.get("public_url")).isEqualTo("https://storage.test/" + storageKey);
        assertThat(row.get("original_name")).isEqualTo("notes.txt");
        assertThat(row.get("mime_type")).isEqualTo("text/plain");
        assertThat(row.get("size_bytes")).isEqualTo(2L);
        assertThat(row.get("access_scope")).isEqualTo("PUBLIC");
        assertThat(row.get("description")).isEqualTo("integration description");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "LAB", "PUBLIC"})
    void standaloneSupportedScopesPersistExactly(String scope) {
        UserFixture owner = insertUser("scope-" + scope.toLowerCase());
        String storageKey = registerStorageKey("scope-" + scope.toLowerCase());
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, null));

        FileResponse response = fileService.upload(textFile("scope.txt", "ok"), scope, null, owner.email());

        assertThat(response.getAccessScope()).isEqualTo(scope);
        assertThat(jdbc.queryForObject(
                "select access_scope from files where storage_key = ?", String.class, storageKey))
                .isEqualTo(scope);
    }

    @Test
    void authorizedProjectMemberUploadPersistsProjectLinkAndProjectScope() {
        UserFixture member = insertUser("project-member");
        assignMemberRole(member.id());
        long projectId = insertProject(member.id());
        insertMembership(projectId, member.id());
        String storageKey = registerStorageKey("project-authorized");
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, null));

        FileResponse response = fileService.uploadForProject(
                textFile("project.txt", "p"), "PROJECT", "project input", member.email(), projectId);

        assertThat(response.getAccessScope()).isEqualTo("PROJECT");
        assertThat(jdbc.queryForObject(
                "select project_id from files where storage_key = ?", Long.class, storageKey))
                .isEqualTo(projectId);
        assertThat(jdbc.queryForObject(
                "select owner_user_id from files where storage_key = ?", Long.class, storageKey))
                .isEqualTo(member.id());
    }

    @Test
    void unauthorizedProjectAccessIsRejectedBeforeStorageAndPersistence() {
        UserFixture member = insertUser("project-unauthorized");
        assignMemberRole(member.id());
        long projectId = insertProject(member.id());
        String storageKey = registerStorageKey("project-denied");

        assertThatThrownBy(() -> fileService.uploadForProject(
                textFile("project.txt", "p"), "PROJECT", null, member.email(), projectId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));

        verify(fileStorage, never()).upload(any(), any(), any(), any());
        assertThat(fileCount(storageKey)).isZero();
    }

    @Test
    void invalidUploadsFailBeforeStorageAndPersistence() {
        UserFixture owner = insertUser("invalid");

        assertStatus(() -> fileService.upload(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]),
                "PRIVATE", null, owner.email()), 400);
        assertStatus(() -> fileService.upload(
                textFile("too-large.txt", "four"), "PRIVATE", null, owner.email()), 413);
        assertStatus(() -> fileService.upload(
                new MockMultipartFile("file", "data.bin", "application/octet-stream", "abc".getBytes()),
                "PRIVATE", null, owner.email()), 415);
        assertStatus(() -> fileService.upload(
                new MockMultipartFile("file", "spoofed.png", "image/png", "abc".getBytes()),
                "PRIVATE", null, owner.email()), 415);
        assertStatus(() -> fileService.upload(
                textFile("project.txt", "p"), "PROJECT", null, owner.email()), 400);

        verify(fileStorage, never()).upload(any(), any(), any(), any());
        assertThat(fileCountForOwner(owner.id())).isZero();
    }

    @Test
    void storageUploadFailureLeavesNoMetadata() {
        UserFixture owner = insertUser("storage-failure");
        String storageKey = registerStorageKey("storage-failure");
        doThrow(new StorageException("storage unavailable"))
                .when(fileStorage).upload(any(), any(), any(), nullable(String.class));

        assertStatus(() -> fileService.upload(
                textFile("failed.txt", "ok"), "PRIVATE", null, owner.email()), 502);

        verify(fileStorage, never()).trash(anyString());
        assertThat(fileCount(storageKey)).isZero();
        assertThat(fileCountForOwner(owner.id())).isZero();
    }

    @Test
    void databaseFailureAfterFlushRollsBackRowAndCleansUploadedStorage() {
        UserFixture owner = insertUser("database-failure");
        String storageKey = registerStorageKey("database-failure-" + "x".repeat(501));
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, null));

        assertThatThrownBy(() -> fileService.upload(
                textFile("failed.txt", "ok"), "PRIVATE", null, owner.email()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        verify(fileStorage).upload(any(), any(), any(), nullable(String.class));
        verify(fileStorage).trash(storageKey);
        assertThat(fileCount(storageKey)).isZero();
        assertThat(fileCountForOwner(owner.id())).isZero();
    }

    @Test
    void outerTransactionRollbackRemovesRowAndTrashesUploadedStorage() {
        UserFixture owner = insertUser("outer-rollback");
        String storageKey = registerStorageKey("outer-rollback");
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, null));

        FileResponse response = transactionTemplate.execute(status -> {
            FileResponse result = fileService.upload(textFile("rollback.txt", "ok"), "PRIVATE", null, owner.email());
            assertThat(fileCount(storageKey)).isEqualTo(1L);
            status.setRollbackOnly();
            return result;
        });

        assertThat(response).isNotNull();
        assertThat(response.getId()).isPositive();
        assertThat(fileCount(storageKey)).isZero();
        verify(fileStorage).trash(storageKey);
    }

    @Test
    void cleanupFailureDoesNotReplacePrimaryOuterTransactionFailure() {
        UserFixture owner = insertUser("cleanup-failure");
        String storageKey = registerStorageKey("cleanup-failure");
        when(fileStorage.upload(any(), any(), any(), nullable(String.class)))
                .thenReturn(new FileStorage.StoredFile(storageKey, null));
        doThrow(new StorageException("cleanup unavailable")).when(fileStorage).trash(storageKey);
        IllegalStateException primaryFailure = new IllegalStateException("primary transaction failure");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            fileService.upload(textFile("rollback.txt", "ok"), "PRIVATE", null, owner.email());
            throw primaryFailure;
        })).isSameAs(primaryFailure);

        verify(fileStorage).trash(storageKey);
        assertThat(fileCount(storageKey)).isZero();
        assertThat(fileCountForOwner(owner.id())).isZero();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private UserFixture insertUser(String label) {
        String marker = "p0-01-" + label + "-" + UUID.randomUUID();
        long id = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, UUID.randomUUID().toString(), marker, marker + "@example.test", "test-password");
        userIds.add(id);
        return new UserFixture(id, marker + "@example.test");
    }

    private void assignMemberRole(long userId) {
        int rows = jdbc.update("""
                insert into user_roles (user_id, role_id, assigned_by)
                select ?, id, ? from roles where code = 'MEMBER'
                """, userId, ASSIGNED_BY);
        assertThat(rows).isEqualTo(1);
    }

    private long insertProject(long creatorId) {
        String code = "P001-" + UUID.randomUUID().toString().substring(0, 20);
        long projectId = jdbc.queryForObject("""
                insert into projects (
                    code, name, project_type, leader_user_id, status, is_public,
                    is_featured, created_by_user_id
                ) values (?, 'P0-01 upload project', 'RESEARCH', ?, 'IN_PROGRESS', false, false, ?)
                returning id
                """, Long.class, code, creatorId, creatorId);
        projectIds.add(projectId);
        return projectId;
    }

    private void insertMembership(long projectId, long userId) {
        jdbc.update("""
                insert into project_members (project_id, user_id, project_role, status, joined_at)
                values (?, ?, 'MEMBER', 'ACTIVE', now())
                """, projectId, userId);
    }

    private String registerStorageKey(String label) {
        String key = "p0-01-" + label + "-" + UUID.randomUUID();
        storageKeys.add(key);
        return key;
    }

    private long fileCount(String storageKey) {
        return jdbc.queryForObject("select count(*) from files where storage_key = ?", Long.class, storageKey);
    }

    private long fileCountForOwner(long ownerId) {
        return jdbc.queryForObject("select count(*) from files where owner_user_id = ?", Long.class, ownerId);
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }

    private record UserFixture(long id, String email) {
    }
}
