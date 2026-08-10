package com.smartlab.service.impl;

import com.smartlab.entity.AuditLogEntity;
import com.smartlab.repo.AuditLogRepository;
import com.smartlab.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditLogPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService auditService;
    @Autowired AuditLogRepository auditLogs;

    private final List<Long> auditIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private Counts baseline;

    @BeforeEach void before() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("smartlab_rich_editor_it");
        baseline = counts();
    }

    @AfterEach void after() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        auditIds.forEach(id -> jdbc.update("delete from audit_logs where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        assertThat(counts()).isEqualTo(baseline);
    }

    @Test void pg1ToPg5AppendDatabaseTimestampNullContextJsonbAndAppendOnlyService() {
        UserFixture actor = insertUser("actor");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.email(), null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.addHeader("User-Agent", "N2 audit integration agent");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.log("ROLE_UPDATED", "ROLE", "77",
                Map.of("name", "Before", "nested", Map.of("active", true)),
                Map.of("name", "After", "codes", List.of("A", "B")));
        Long firstId = newestAuditId("ROLE_UPDATED", "77"); auditIds.add(firstId);
        AuditRow row = read(firstId);
        assertThat(row.actorUserId()).isEqualTo(actor.id());
        assertThat(row.action()).isEqualTo("ROLE_UPDATED");
        assertThat(row.targetType()).isEqualTo("ROLE");
        assertThat(row.beforeJson()).contains("\"nested\"");
        assertThat(row.afterJson()).contains("\"codes\"");
        assertThat(row.ip()).isEqualTo("203.0.113.9");
        assertThat(row.userAgent()).isEqualTo("N2 audit integration agent");
        assertThat(row.createdAt()).isNotNull();
        assertThat(auditLogs.findById(firstId).orElseThrow().getCreatedAt()).isNotNull();

        SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes();
        auditService.log("POST_REVIEWED", "POST", null, null, null);
        Long nullId = newestAuditId("POST_REVIEWED", null); auditIds.add(nullId);
        AuditRow nullRow = read(nullId);
        assertThat(nullRow.actorUserId()).isNull();
        assertThat(nullRow.ip()).isNull();
        assertThat(nullRow.userAgent()).isNull();
        assertThat(AuditService.class.getMethods()).extracting(java.lang.reflect.Method::getName).containsExactly("log");
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString().replace("-", "");
        String userId = "a2" + marker; String email = "a2-" + tag + "-" + marker + "@test";
        Long id = jdbc.queryForObject("insert into tbl_user(user_id,name,email,password,is_active,is_account_verified,reset_otp_expire_at) values(?,?,?,?,true,true,0) returning id",
                Long.class, userId, "A2 " + tag, email, "integration-only");
        userIds.add(id); return new UserFixture(id, userId, email);
    }
    private Long newestAuditId(String action, String targetId) {
        String sql = targetId == null
                ? "select id from audit_logs where action = ? and target_id is null order by id desc limit 1"
                : "select id from audit_logs where action = ? and target_id = ? order by id desc limit 1";
        return targetId == null ? jdbc.queryForObject(sql, Long.class, action) : jdbc.queryForObject(sql, Long.class, action, targetId);
    }
    private AuditRow read(Long id) {
        return jdbc.queryForObject("select actor_user_id,action,target_type,before_json::text,after_json::text,ip_address,user_agent,created_at from audit_logs where id=?",
                (rs, n) -> new AuditRow((Long) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant()), id);
    }
    private Counts counts() { return new Counts(count("audit_logs"), count("tbl_user"), count("posts"), count("post_reviews"),
            count("notifications")); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private record UserFixture(Long id, String userId, String email) {}
    private record AuditRow(Long actorUserId, String action, String targetType, String beforeJson, String afterJson,
                            String ip, String userAgent, java.time.Instant createdAt) {}
    private record Counts(long audits, long users, long posts, long reviews, long notifications) {}
}
