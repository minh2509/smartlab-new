package com.smartlab.service.impl;

import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.service.AdminAccountService;
import com.smartlab.service.TokenHashService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "jwt.secret.key=d1-auth-postgres-integration-secret-for-tests-only",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AdminAccountInvitationPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AdminAccountService adminAccountService;
    @Autowired
    private TokenHashService tokenHashService;

    @Value("${smartlab.test.target-database:smartlab_d1_auth_it}")
    private String targetDatabase;

    private final Set<Long> invitationIds = new LinkedHashSet<>();
    private final Set<Long> userIds = new LinkedHashSet<>();
    private DatabaseCounts baseline;

    @BeforeEach
    void requireExactDisposableDatabase() {
        assertThat(targetDatabase).isNotEqualTo("smartlab");
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        baseline = counts();
    }

    @AfterEach
    void cleanFixturesAndVerifyBaseline() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);

        for (Long invitationId : invitationIds) {
            jdbc.update("delete from account_invitations where id = ?", invitationId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from member_research_fields where member_profile_id in (select id from member_profiles where user_id = ?)", userId);
            jdbc.update("delete from member_profiles where user_id = ?", userId);
            jdbc.update("delete from user_sessions where user_id = ?", userId);
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from user_permission_overrides where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }

        invitationIds.clear();
        userIds.clear();
        assertThat(counts()).isEqualTo(baseline);
    }

    @Test
    void expiredInvitationStatusPersistsAfterRejectedAcceptance() {
        String marker = UUID.randomUUID().toString();
        String rawToken = "expired-invite-" + marker;
        String email = "expired-invite-" + marker + "@example.test";
        Long userId = insertInactiveUser(marker, email);
        Long invitationId = insertExpiredInvitation(marker, email, rawToken);

        InvitationAcceptRequest request = new InvitationAcceptRequest();
        request.setToken(rawToken);
        request.setPassword("Member@123");

        assertThatThrownBy(() -> adminAccountService.acceptInvite(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        String persistedStatus = jdbc.queryForObject(
                "select status from account_invitations where id = ?", String.class, invitationId
        );
        assertThat(persistedStatus).isEqualTo(InvitationStatus.EXPIRED.name());
        assertThat(userId).isIn(userIds);
    }

    private Long insertInactiveUser(String marker, String email) {
        Long userId = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, false, false)
                returning id
                """, Long.class, marker,
                "A3 Expired Invite", email, "temporary-password");
        userIds.add(userId);
        return userId;
    }

    private Long insertExpiredInvitation(String marker, String email, String rawToken) {
        Long invitationId = jdbc.queryForObject("""
                insert into account_invitations (
                    invitation_id, email, token_hash, status, expires_at, resend_count
                )
                values (?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, marker, email, tokenHashService.sha256(rawToken),
                InvitationStatus.PENDING.name(), java.sql.Timestamp.from(Instant.now().minusSeconds(60)), 0);
        invitationIds.add(invitationId);
        return invitationId;
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private DatabaseCounts counts() {
        return new DatabaseCounts(
                jdbc.queryForObject("select count(*) from tbl_user", Long.class),
                jdbc.queryForObject("select count(*) from member_profiles", Long.class),
                jdbc.queryForObject("select count(*) from account_invitations", Long.class)
        );
    }

    private record DatabaseCounts(long users, long profiles, long invitations) {
    }
}
