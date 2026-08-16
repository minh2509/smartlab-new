package com.smartlab.service.impl;

import com.smartlab.dto.request.RoleRequest;
import com.smartlab.service.AdminRolePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AdminRolePermissionPostgresIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminRolePermissionService service;

    private Long actorId;
    private Long roleId;
    private Counts baseline;

    @BeforeEach
    void before() {
        assertThat(currentDatabase()).isEqualTo(TARGET_DATABASE);
        baseline = counts();
    }

    @AfterEach
    void after() {
        SecurityContextHolder.clearContext();

        if (roleId != null) {
            jdbc.update(
                    "delete from audit_logs where target_type = 'ROLE' and target_id = ?",
                    roleId.toString()
            );
            jdbc.update("delete from roles where id = ?", roleId);
        }

        if (actorId != null) {
            jdbc.update("delete from tbl_user where id = ?", actorId);
        }

        assertThat(counts()).isEqualTo(baseline);
    }

    @Test
    void roleUpdateCommitsBusinessMutationAndMatchingAuditRow() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        String roleCode = ("A2PG" + marker).toUpperCase();
        String actorEmail = "a2-d1-" + marker + "@test";

        actorId = jdbc.queryForObject("""
                insert into tbl_user(
                    user_id,
                    name,
                    email,
                    password,
                    is_active,
                    is_account_verified,
                    reset_otp_expire_at
                )
                values (?, ?, ?, ?, true, true, NULL)
                returning id
                """,
                Long.class,
                "a2" + marker,
                "A2 D1 Actor",
                actorEmail,
                "integration-only"
        );

        roleId = jdbc.queryForObject("""
                insert into roles(
                    code,
                    name,
                    description,
                    is_system,
                    is_active
                )
                values (?, ?, ?, false, true)
                returning id
                """,
                Long.class,
                roleCode,
                "Before Name",
                "Before Description"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actorEmail,
                        null,
                        List.of()
                )
        );

        RoleRequest request = new RoleRequest();
        request.setCode("IGNORED");
        request.setName("After Name");
        request.setDescription("After Description");
        request.setIsActive(false);

        service.updateRole(roleCode.toLowerCase(), request);

        RoleRow persistedRole = jdbc.queryForObject("""
                select code, name, description, is_system, is_active
                from roles
                where id = ?
                """,
                (rs, rowNum) -> new RoleRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBoolean("is_system"),
                        rs.getBoolean("is_active")
                ),
                roleId
        );

        assertThat(persistedRole).isEqualTo(new RoleRow(
                roleCode,
                "After Name",
                "After Description",
                false,
                false
        ));

        AuditRow audit = jdbc.queryForObject("""
                select
                    actor_user_id,
                    action,
                    target_type,
                    target_id,
                    before_json ->> 'code' as before_code,
                    before_json ->> 'name' as before_name,
                    before_json ->> 'description' as before_description,
                    before_json ->> 'isSystem' as before_system,
                    before_json ->> 'isActive' as before_active,
                    after_json ->> 'code' as after_code,
                    after_json ->> 'name' as after_name,
                    after_json ->> 'description' as after_description,
                    after_json ->> 'isSystem' as after_system,
                    after_json ->> 'isActive' as after_active,
                    created_at
                from audit_logs
                where action = 'ROLE_UPDATED'
                  and target_type = 'ROLE'
                  and target_id = ?
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> new AuditRow(
                        (Long) rs.getObject("actor_user_id"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("before_code"),
                        rs.getString("before_name"),
                        rs.getString("before_description"),
                        rs.getString("before_system"),
                        rs.getString("before_active"),
                        rs.getString("after_code"),
                        rs.getString("after_name"),
                        rs.getString("after_description"),
                        rs.getString("after_system"),
                        rs.getString("after_active"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                roleId.toString()
        );

        assertThat(audit.actorUserId()).isEqualTo(actorId);
        assertThat(audit.action()).isEqualTo("ROLE_UPDATED");
        assertThat(audit.targetType()).isEqualTo("ROLE");
        assertThat(audit.targetId()).isEqualTo(roleId.toString());

        assertThat(audit.beforeCode()).isEqualTo(roleCode);
        assertThat(audit.beforeName()).isEqualTo("Before Name");
        assertThat(audit.beforeDescription()).isEqualTo("Before Description");
        assertThat(audit.beforeSystem()).isEqualTo("false");
        assertThat(audit.beforeActive()).isEqualTo("true");

        assertThat(audit.afterCode()).isEqualTo(roleCode);
        assertThat(audit.afterName()).isEqualTo("After Name");
        assertThat(audit.afterDescription()).isEqualTo("After Description");
        assertThat(audit.afterSystem()).isEqualTo("false");
        assertThat(audit.afterActive()).isEqualTo("false");

        assertThat(audit.createdAt()).isNotNull();

        assertThat(jdbc.queryForObject(
                """
                select count(*)
                from audit_logs
                where action = 'ROLE_UPDATED'
                  and target_type = 'ROLE'
                  and target_id = ?
                """,
                Long.class,
                roleId.toString()
        )).isEqualTo(1L);
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private Counts counts() {
        return new Counts(
                count("audit_logs"),
                count("tbl_user"),
                count("roles"),
                count("permissions"),
                count("role_permissions"),
                count("user_roles"),
                count("user_permission_overrides")
        );
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table,
                Long.class
        );
    }

    private record RoleRow(
            String code,
            String name,
            String description,
            boolean system,
            boolean active
    ) {
    }

    private record AuditRow(
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String beforeCode,
            String beforeName,
            String beforeDescription,
            String beforeSystem,
            String beforeActive,
            String afterCode,
            String afterName,
            String afterDescription,
            String afterSystem,
            String afterActive,
            Instant createdAt
    ) {
    }

    private record Counts(
            long audits,
            long users,
            long roles,
            long permissions,
            long rolePermissions,
            long userRoles,
            long permissionOverrides
    ) {
    }
}
