package com.smartlab.integration;

import com.smartlab.service.FileService;
import com.smartlab.storage.FileStorage;
import com.smartlab.storage.StorageException;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=b2-p1-01-postgres-integration-secret",
        "smartlab.email-outbox.max-per-poll=0"
})
class FileDeletionPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";
    private static final String EMAIL_SUFFIX = "@b2-p1-01.example.test";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FileService fileService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FileStorage fileStorage;

    private TransactionTemplate transactionTemplate;
    private Fixture fixture;

    @BeforeEach
    void requireExactIsolatedDatabaseAndPrepareTransactionTemplate() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        assertThat(currentUser()).isEqualTo("smartlab_user");
        transactionTemplate = new TransactionTemplate(transactionManager);
        fixture = insertFixture();
    }

    @AfterEach
    void removeOnlyThisTestFixtures() {
        if (fixture == null) return;
        jdbc.update("delete from files where storage_key = ?", fixture.storageKey());
        jdbc.update("delete from tbl_user where email = ?", fixture.email());
        reset(fileStorage);
    }

    @Test
    void committedDeleteLeavesDatabaseSoftDeletedAndStorageTrashed() {
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> {
            trashed.set(true);
            return null;
        }).when(fileStorage).trash(fixture.storageKey());

        transactionTemplate.executeWithoutResult(status ->
                fileService.delete(fixture.fileId(), fixture.email(), authentication()));

        assertThat(deletedAtPresent()).isTrue();
        assertThat(trashed).isTrue();
        verify(fileStorage).trash(fixture.storageKey());
        verify(fileStorage, never()).restore(anyString());
    }

    @Test
    void ambiguousTrashFailureLeavesDatabaseActiveAndRestoresStorage() {
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> {
            trashed.set(true);
            throw new StorageException("remote response failed after trash");
        }).when(fileStorage).trash(fixture.storageKey());
        doAnswer(invocation -> {
            trashed.set(false);
            return null;
        }).when(fileStorage).restore(fixture.storageKey());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                fileService.delete(fixture.fileId(), fixture.email(), authentication())))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(deletedAtPresent()).isFalse();
        assertThat(trashed).isFalse();
        verify(fileStorage).trash(fixture.storageKey());
        verify(fileStorage).restore(fixture.storageKey());
    }

    @Test
    void failureAfterFlushRollsBackDatabaseAndRestoresStorage() {
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> {
            trashed.set(true);
            return null;
        }).when(fileStorage).trash(fixture.storageKey());
        doAnswer(invocation -> {
            trashed.set(false);
            return null;
        }).when(fileStorage).restore(fixture.storageKey());
        IllegalStateException primaryFailure = new IllegalStateException("controlled failure after flush");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            fileService.delete(fixture.fileId(), fixture.email(), authentication());
            assertThat(deletedAtPresent()).isTrue();
            throw primaryFailure;
        })).isSameAs(primaryFailure);

        assertThat(deletedAtPresent()).isFalse();
        assertThat(trashed).isFalse();
        verify(fileStorage).trash(fixture.storageKey());
        verify(fileStorage).restore(fixture.storageKey());
    }

    @Test
    void outerTransactionRollbackAfterServiceReturnRestoresStorageAndDbState() {
        AtomicBoolean trashed = new AtomicBoolean();
        doAnswer(invocation -> {
            trashed.set(true);
            return null;
        }).when(fileStorage).trash(fixture.storageKey());
        doAnswer(invocation -> {
            trashed.set(false);
            return null;
        }).when(fileStorage).restore(fixture.storageKey());

        transactionTemplate.executeWithoutResult(status -> {
            fileService.delete(fixture.fileId(), fixture.email(), authentication());
            assertThat(deletedAtPresent()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(deletedAtPresent()).isFalse();
        assertThat(trashed).isFalse();
        verify(fileStorage).trash(fixture.storageKey());
        verify(fileStorage).restore(fixture.storageKey());
    }

    @Test
    void restoreFailureDoesNotReplacePrimaryOuterTransactionFailure() {
        doAnswer(invocation -> null).when(fileStorage).trash(fixture.storageKey());
        doThrow(new StorageException("restore failed")).when(fileStorage).restore(fixture.storageKey());
        IllegalStateException primaryFailure = new IllegalStateException("primary transaction failure");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            fileService.delete(fixture.fileId(), fixture.email(), authentication());
            throw primaryFailure;
        })).isSameAs(primaryFailure);

        assertThat(deletedAtPresent()).isFalse();
        verify(fileStorage).trash(fixture.storageKey());
        verify(fileStorage).restore(fixture.storageKey());
    }

    private Fixture insertFixture() {
        String marker = "b2-p1-01-it-" + UUID.randomUUID();
        String email = marker + EMAIL_SUFFIX;
        long userId = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, UUID.randomUUID().toString(), marker, email, "test-password");
        String storageKey = marker + "-storage";
        long fileId = jdbc.queryForObject("""
                insert into files (
                    owner_user_id, storage_provider, storage_key, original_name,
                    mime_type, size_bytes, access_scope
                ) values (?, 'TEST', ?, ?, 'text/plain', 1, 'PRIVATE')
                returning id
                """, Long.class, userId, storageKey, marker + ".txt");
        return new Fixture(userId, fileId, email, storageKey);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(fixture.email(), null, java.util.List.of());
    }

    private boolean deletedAtPresent() {
        return jdbc.queryForObject(
                "select deleted_at is not null from files where id = ?", Boolean.class, fixture.fileId());
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private String currentUser() {
        return jdbc.queryForObject("select current_user", String.class);
    }

    private record Fixture(long userId, long fileId, String email, String storageKey) {
    }
}
