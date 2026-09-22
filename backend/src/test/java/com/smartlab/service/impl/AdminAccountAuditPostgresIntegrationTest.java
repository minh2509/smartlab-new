package com.smartlab.service.impl;

import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.entity.MemberProfileEntity;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.PermissionOverrideEffect;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AdminAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdminAccountAuditPostgresIntegrationTest {
    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private MemberProfileRepository memberProfiles;
    @Autowired private PermissionRepository permissions;
    @Autowired private AdminAccountService accounts;

    @BeforeEach
    void before() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(TARGET_DATABASE);
    }

    @AfterEach
    void after() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountStatusAndPermissionOverridePersistActorTargetAndBeforeAfter() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        UserEntity actor = users.save(user(UUID.randomUUID().toString(), "audit-actor-" + marker + "@test"));
        UserEntity target = users.save(user(UUID.randomUUID().toString(), "audit-target-" + marker + "@test"));
        memberProfiles.save(MemberProfileEntity.create(target));
        PermissionEntity permission = permissions.save(PermissionEntity.builder()
                .code(("AUDIT_PERMISSION_" + marker).toUpperCase())
                .name("Audit permission")
                .module("AUDIT_TEST")
                .description("Integration-only permission")
                .isActive(true)
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getEmail(), null, List.of()));

        accounts.setActive(target.getUserId(), false);

        AccountStatusAudit statusAudit = jdbc.queryForObject("""
                select actor_user_id, action, target_type, target_id,
                       before_json ->> 'isActive' as before_active,
                       before_json ->> 'activeStatus' as before_status,
                       after_json ->> 'isActive' as after_active,
                       after_json ->> 'activeStatus' as after_status
                from audit_logs
                where action = 'USER_ACTIVE_STATUS_UPDATED' and target_id = ?
                order by id desc limit 1
                """, (rs, row) -> new AccountStatusAudit(
                (Long) rs.getObject("actor_user_id"), rs.getString("action"),
                rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("before_active"), rs.getString("before_status"),
                rs.getString("after_active"), rs.getString("after_status")), target.getId().toString());

        assertThat(statusAudit).isEqualTo(new AccountStatusAudit(
                actor.getId(), "USER_ACTIVE_STATUS_UPDATED", "USER", target.getId().toString(),
                "true", "ACTIVE", "false", "INACTIVE"));

        PermissionOverrideRequest request = new PermissionOverrideRequest();
        request.setEffect(PermissionOverrideEffect.DENY);
        accounts.setPermissionOverride(target.getUserId(), permission.getCode(), request, actor.getEmail());

        OverrideAudit overrideAudit = jdbc.queryForObject("""
                select actor_user_id, action, target_type, target_id,
                       before_json ->> 'permissionCode' as before_code,
                       before_json ->> 'effect' as before_effect,
                       after_json ->> 'permissionCode' as after_code,
                       after_json ->> 'effect' as after_effect
                from audit_logs
                where action = 'USER_PERMISSION_OVERRIDE_SET' and target_id = ?
                order by id desc limit 1
                """, (rs, row) -> new OverrideAudit(
                (Long) rs.getObject("actor_user_id"), rs.getString("action"),
                rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("before_code"), rs.getString("before_effect"),
                rs.getString("after_code"), rs.getString("after_effect")), target.getId().toString());

        assertThat(overrideAudit).isEqualTo(new OverrideAudit(
                actor.getId(), "USER_PERMISSION_OVERRIDE_SET", "USER", target.getId().toString(),
                permission.getCode(), null, permission.getCode(), "DENY"));
    }

    private static UserEntity user(String userId, String email) {
        return UserEntity.builder()
                .userId(userId)
                .name("Audit Test")
                .email(email)
                .password("integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .build();
    }

    private record AccountStatusAudit(Long actorId, String action, String targetType, String targetId,
                                      String beforeActive, String beforeStatus,
                                      String afterActive, String afterStatus) {}

    private record OverrideAudit(Long actorId, String action, String targetType, String targetId,
                                 String beforeCode, String beforeEffect,
                                 String afterCode, String afterEffect) {}
}
