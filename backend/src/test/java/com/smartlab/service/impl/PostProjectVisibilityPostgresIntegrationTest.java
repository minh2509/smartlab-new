package com.smartlab.service.impl;

import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PostProjectVisibilityPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private PostService postService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    private final List<String> userIds = new ArrayList<>();
    private final List<String> projectCodes = new ArrayList<>();
    private final List<String> postSlugs = new ArrayList<>();

    @BeforeEach
    void requiresTheDedicatedIntegrationDatabase() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
    }

    @AfterTransaction
    void fixturesWereRolledBack() {
        postSlugs.forEach(slug -> assertThat(rowCount("select count(*) from posts where slug = ?", slug)).isZero());
        projectCodes.forEach(code -> assertThat(rowCount("select count(*) from projects where code = ?", code)).isZero());
        userIds.forEach(userId -> assertThat(rowCount("select count(*) from tbl_user where user_id = ?", userId)).isZero());
    }

    @Test
    void activeProjectMemberCanReadPublishedProjectPostButNonMemberAndRemovedMemberCannot() {
        UserFixture author = insertUser("author");
        UserFixture member = insertUser("member");
        UserFixture outsider = insertUser("outsider");
        long projectId = insertProject(author.databaseId());
        insertMembership(projectId, member.databaseId(), "ACTIVE");
        String slug = insertPublishedProjectPost(author.databaseId(), projectId);

        assertThat(postService.getPostBySlug(member.email(), slug).getProjectId()).isEqualTo(projectId);
        assertThat(postService.getReadablePosts(member.email()))
                .extracting(PostSummaryResponse::getSlug)
                .contains(slug);

        assertNotFound(() -> postService.getPostBySlug(outsider.email(), slug));
        assertThat(postService.getReadablePosts(outsider.email()))
                .extracting(PostSummaryResponse::getSlug)
                .doesNotContain(slug);

        jdbc.update("update project_members set status = 'REMOVED' where project_id = ? and user_id = ?",
                projectId, member.databaseId());
        assertNotFound(() -> postService.getPostBySlug(member.email(), slug));
        assertThat(postService.getReadablePosts(member.email()))
                .extracting(PostSummaryResponse::getSlug)
                .doesNotContain(slug);
    }

    @Test
    void softDeletedProjectNoLongerGrantsPublishedProjectPostAccess() {
        UserFixture author = insertUser("soft-author");
        UserFixture member = insertUser("soft-member");
        long projectId = insertProject(author.databaseId());
        insertMembership(projectId, member.databaseId(), "ACTIVE");
        String slug = insertPublishedProjectPost(author.databaseId(), projectId);

        jdbc.update("update projects set deleted_at = now() where id = ?", projectId);

        assertNotFound(() -> postService.getPostBySlug(member.email(), slug));
        assertThat(postService.getReadablePosts(member.email()))
                .extracting(PostSummaryResponse::getSlug)
                .doesNotContain(slug);
    }

    private UserFixture insertUser(String label) {
        String userId = "project-visibility-" + UUID.randomUUID();
        String email = userId + "@example.test";
        userIds.add(userId);
        Long databaseId = jdbc.queryForObject(
                """
                        insert into tbl_user (user_id, name, email, password, is_active, is_account_verified, reset_otp_expire_at)
                        values (?, ?, ?, '', true, true, 0)
                        returning id
                        """,
                Long.class,
                userId,
                "Project visibility " + label,
                email
        );
        return new UserFixture(databaseId, email);
    }

    private long insertProject(long createdByUserId) {
        String code = "POST-VIS-" + UUID.randomUUID().toString().substring(0, 8);
        projectCodes.add(code);
        return jdbc.queryForObject(
                """
                        insert into projects (
                            code, name, project_type, status, is_public, is_featured, created_by_user_id, created_at, updated_at
                        ) values (?, 'Post visibility project', 'RESEARCH', 'PROPOSED', false, false, ?, now(), now())
                        returning id
                        """,
                Long.class,
                code,
                createdByUserId
        );
    }

    private void insertMembership(long projectId, long userId, String status) {
        jdbc.update(
                """
                        insert into project_members (project_id, user_id, project_role, status, joined_at)
                        values (?, ?, 'MEMBER', ?, now())
                        """,
                projectId,
                userId,
                status
        );
    }

    private String insertPublishedProjectPost(long authorUserId, long projectId) {
        String slug = "project-visibility-" + UUID.randomUUID();
        postSlugs.add(slug);
        jdbc.update(
                """
                        insert into posts (author_user_id, project_id, title, slug, excerpt, content_json, visibility, status, created_at, updated_at)
                        values (?, ?, 'Published project post', ?, 'Integration acceptance', '{}'::jsonb,
                                'PROJECT', 'PUBLISHED', now(), now())
                        """,
                authorUserId,
                projectId,
                slug
        );
        return slug;
    }

    private int rowCount(String sql, String value) {
        Integer count = jdbc.queryForObject(sql, Integer.class, value);
        return count == null ? 0 : count;
    }

    private String currentDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getCatalog();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect PostgreSQL database", exception);
        }
    }

    private static void assertNotFound(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private record UserFixture(Long databaseId, String email) {
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
