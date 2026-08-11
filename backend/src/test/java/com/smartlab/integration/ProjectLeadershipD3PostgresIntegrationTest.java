package com.smartlab.integration;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.ProjectLeaderResponse;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProjectLeadershipD3PostgresIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@smartlab.local";

    @Autowired
    private ProjectService projectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdProjectCodes = new ArrayList<>();
    private final List<String> createdUserIds = new ArrayList<>();

    @AfterTransaction
    void verifiesFixturesWereRolledBack() {
        createdProjectCodes.forEach(code -> assertThat(rowCount(
                "select count(*) from projects where code = ?",
                code
        )).as("project fixture %s", code).isZero());
        createdUserIds.forEach(userId -> assertThat(rowCount(
                "select count(*) from tbl_user where user_id = ?",
                userId
        )).as("user fixture %s", userId).isZero());
    }

    @Test
    void createsProjectWithoutPrimaryAndAssignsPrimaryWithoutChangingGlobalRoles() {
        TestUser leader = insertMember("late-primary");
        List<UserRoleSnapshot> rolesBefore = roleAssignments(leader.databaseId());

        ProjectResponse created = createProject("no-primary");

        assertThat(created.getPrimaryLeader()).isNull();
        assertThat(created.getLeaders()).isEmpty();
        assertThat(primaryLeaderDatabaseId(created.getId())).isNull();
        assertThat(projectMembershipCount(created.getId())).isZero();

        ProjectResponse assigned = changePrimary(created.getId(), leader.userId());

        assertThat(assigned.getPrimaryLeader().getUserId()).isEqualTo(leader.userId());
        assertThat(assigned.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly(leader.userId());
        assertThat(primaryLeaderDatabaseId(created.getId())).isEqualTo(leader.databaseId());
        assertMembership(created.getId(), leader.databaseId(), "LEADER", "ACTIVE");
        assertThat(roleAssignments(leader.databaseId())).isEqualTo(rolesBefore);
    }

    @Test
    void replaceLeadersCanRemovePrimaryAndThenClearEveryLeader() {
        TestUser primary = insertMember("primary");
        TestUser coLeader = insertMember("co-leader");
        List<UserRoleSnapshot> primaryRoles = roleAssignments(primary.databaseId());
        List<UserRoleSnapshot> coLeaderRoles = roleAssignments(coLeader.databaseId());
        ProjectResponse project = createProject("replace-primary");
        changePrimary(project.getId(), primary.userId());
        replaceLeaders(project.getId(), List.of(primary.userId(), coLeader.userId()));

        ProjectResponse withoutPrimary = replaceLeaders(project.getId(), List.of(coLeader.userId()));

        assertThat(withoutPrimary.getPrimaryLeader()).isNull();
        assertThat(withoutPrimary.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly(coLeader.userId());
        assertThat(primaryLeaderDatabaseId(project.getId())).isNull();
        assertMembership(project.getId(), primary.databaseId(), "MEMBER", "ACTIVE");
        assertMembership(project.getId(), coLeader.databaseId(), "LEADER", "ACTIVE");
        assertThat(roleAssignments(primary.databaseId())).isEqualTo(primaryRoles);
        assertThat(roleAssignments(coLeader.databaseId())).isEqualTo(coLeaderRoles);

        ProjectResponse withoutLeaders = replaceLeaders(project.getId(), List.of());

        assertThat(withoutLeaders.getPrimaryLeader()).isNull();
        assertThat(withoutLeaders.getLeaders()).isEmpty();
        assertMembership(project.getId(), coLeader.databaseId(), "MEMBER", "ACTIVE");
        assertThat(roleAssignments(primary.databaseId())).isEqualTo(primaryRoles);
        assertThat(roleAssignments(coLeader.databaseId())).isEqualTo(coLeaderRoles);
    }

    @Test
    void sameGlobalMemberCanLeadMultipleProjectsWithoutChangingGlobalRoles() {
        TestUser sharedLeader = insertMember("shared-leader");
        List<UserRoleSnapshot> rolesBefore = roleAssignments(sharedLeader.databaseId());
        ProjectResponse firstProject = createProject("shared-first");
        ProjectResponse secondProject = createProject("shared-second");
        changePrimary(firstProject.getId(), sharedLeader.userId());
        changePrimary(secondProject.getId(), sharedLeader.userId());

        assertMembership(firstProject.getId(), sharedLeader.databaseId(), "LEADER", "ACTIVE");
        assertMembership(secondProject.getId(), sharedLeader.databaseId(), "LEADER", "ACTIVE");
        assertThat(roleAssignments(sharedLeader.databaseId())).isEqualTo(rolesBefore);

        replaceLeaders(firstProject.getId(), List.of());

        assertThat(primaryLeaderDatabaseId(firstProject.getId())).isNull();
        assertMembership(firstProject.getId(), sharedLeader.databaseId(), "MEMBER", "ACTIVE");
        assertMembership(secondProject.getId(), sharedLeader.databaseId(), "LEADER", "ACTIVE");
        assertThat(roleAssignments(sharedLeader.databaseId())).isEqualTo(rolesBefore);

        replaceLeaders(secondProject.getId(), List.of());

        assertThat(primaryLeaderDatabaseId(secondProject.getId())).isNull();
        assertMembership(secondProject.getId(), sharedLeader.databaseId(), "MEMBER", "ACTIVE");
        assertThat(roleAssignments(sharedLeader.databaseId())).isEqualTo(rolesBefore);
    }

    @Test
    void activeProjectLeaderWithoutGlobalProjectManageCanUpdateButOutsiderCannot() {
        TestUser projectLeader = insertMember("update-leader");
        TestUser outsider = insertMember("update-outsider");
        List<UserRoleSnapshot> leaderRoles = roleAssignments(projectLeader.databaseId());
        List<UserRoleSnapshot> outsiderRoles = roleAssignments(outsider.databaseId());
        ProjectResponse project = createProject("membership-update");
        replaceLeaders(project.getId(), List.of(projectLeader.userId()));

        assertThat(hasGlobalRolePermission(projectLeader.databaseId(), "PROJECT_MANAGE")).isFalse();
        UpdateProjectRequest leaderUpdate = new UpdateProjectRequest();
        leaderUpdate.setName("Updated by membership leader");

        ProjectResponse updated = projectService.update(
                project.getId(),
                leaderUpdate,
                projectLeader.email()
        );

        assertThat(updated.getName()).isEqualTo("Updated by membership leader");
        assertThat(projectName(project.getId())).isEqualTo("Updated by membership leader");

        UpdateProjectRequest outsiderUpdate = new UpdateProjectRequest();
        outsiderUpdate.setName("Outsider update");
        assertThatThrownBy(() -> projectService.update(
                project.getId(),
                outsiderUpdate,
                outsider.email()
        )).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
        );

        assertThat(projectName(project.getId())).isEqualTo("Updated by membership leader");
        assertThat(roleAssignments(projectLeader.databaseId())).isEqualTo(leaderRoles);
        assertThat(roleAssignments(outsider.databaseId())).isEqualTo(outsiderRoles);
    }

    @Test
    void atomicLeadershipUpdateIsIdempotentAndPreservesGlobalRoles() {
        TestUser primary = insertMember("atomic-primary");
        TestUser coLeader = insertMember("atomic-co-leader");
        List<UserRoleSnapshot> primaryRoles = roleAssignments(primary.databaseId());
        List<UserRoleSnapshot> coLeaderRoles = roleAssignments(coLeader.databaseId());
        ProjectResponse project = createProject("atomic-leadership");

        ProjectResponse firstUpdate = updateLeadership(
                project.getId(),
                primary.userId(),
                List.of(primary.userId(), coLeader.userId())
        );
        ProjectResponse repeatedUpdate = updateLeadership(
                project.getId(),
                primary.userId(),
                List.of(primary.userId(), coLeader.userId())
        );

        assertThat(firstUpdate.getPrimaryLeader().getUserId()).isEqualTo(primary.userId());
        assertThat(repeatedUpdate.getPrimaryLeader().getUserId()).isEqualTo(primary.userId());
        assertThat(repeatedUpdate.getLeaders())
                .extracting(ProjectLeaderResponse::getUserId)
                .containsExactly(primary.userId(), coLeader.userId());
        assertThat(primaryLeaderDatabaseId(project.getId())).isEqualTo(primary.databaseId());
        assertMembership(project.getId(), primary.databaseId(), "LEADER", "ACTIVE");
        assertMembership(project.getId(), coLeader.databaseId(), "LEADER", "ACTIVE");
        assertThat(projectMembershipCount(project.getId())).isEqualTo(2);
        assertThat(roleAssignments(primary.databaseId())).isEqualTo(primaryRoles);
        assertThat(roleAssignments(coLeader.databaseId())).isEqualTo(coLeaderRoles);

        updateLeadership(project.getId(), coLeader.userId(), List.of(coLeader.userId()));

        assertThat(primaryLeaderDatabaseId(project.getId())).isEqualTo(coLeader.databaseId());
        assertMembership(project.getId(), primary.databaseId(), "MEMBER", "ACTIVE");
        assertMembership(project.getId(), coLeader.databaseId(), "LEADER", "ACTIVE");
        assertThat(roleAssignments(primary.databaseId())).isEqualTo(primaryRoles);
        assertThat(roleAssignments(coLeader.databaseId())).isEqualTo(coLeaderRoles);
    }

    @Test
    void persistsProjectMemberAuditSnapshotsWithoutIdempotentNoise() {
        TestUser leader = insertMember("audit-leader");
        ProjectResponse project = createProject("audit-membership");

        assertThat(projectMemberAuditCount(project.getId())).isZero();

        changePrimary(project.getId(), leader.userId());

        Long membershipId = membershipId(project.getId(), leader.databaseId());
        ProjectMemberAuditRow created = latestProjectMemberAudit(membershipId);
        assertThat(created.action()).isEqualTo("PROJECT_MEMBER_CREATED");
        assertThat(created.targetType()).isEqualTo("PROJECT_MEMBER");
        assertThat(created.targetId()).isEqualTo(membershipId.toString());
        assertThat(created.beforeJson()).isNull();
        assertThat(created.afterProjectId()).isEqualTo(project.getId().toString());
        assertThat(created.afterUserId()).isEqualTo(leader.databaseId().toString());
        assertThat(created.afterRole()).isEqualTo("LEADER");
        assertThat(created.afterStatus()).isEqualTo("ACTIVE");

        replaceLeaders(project.getId(), List.of());

        ProjectMemberAuditRow updated = latestProjectMemberAudit(membershipId);
        assertThat(updated.action()).isEqualTo("PROJECT_MEMBER_UPDATED");
        assertThat(updated.targetId()).isEqualTo(membershipId.toString());
        assertThat(updated.beforeRole()).isEqualTo("LEADER");
        assertThat(updated.beforeStatus()).isEqualTo("ACTIVE");
        assertThat(updated.afterRole()).isEqualTo("MEMBER");
        assertThat(updated.afterStatus()).isEqualTo("ACTIVE");

        int auditCountBeforeRepeat = projectMemberAuditCount(project.getId());
        replaceLeaders(project.getId(), List.of());
        assertThat(projectMemberAuditCount(project.getId())).isEqualTo(auditCountBeforeRepeat);
    }

    @Test
    void leaderCandidateSearchIsCaseInsensitiveFilteredOrderedAndCappedAtTwenty() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String commonLabel = "candidate-search-" + suffix;
        TestUser[] eligibleByNameOrder = new TestUser[22];
        for (int index = 21; index >= 0; index--) {
            eligibleByNameOrder[index] = insertMember(commonLabel + "-" + String.format("%02d", index));
        }

        TestUser inactiveAccount = insertMember(commonLabel + "-00-inactive-account");
        jdbcTemplate.update(
                "update tbl_user set is_active = false where id = ?",
                inactiveAccount.databaseId()
        );
        TestUser inactiveRoleAccount = insertMember(commonLabel + "-00-inactive-role");
        assignInactiveRole(inactiveRoleAccount.databaseId(), suffix);

        List<LeaderCandidateResponse> candidates = projectService.findLeaderCandidates(
                commonLabel.toUpperCase(),
                ADMIN_EMAIL
        );

        assertThat(candidates).hasSize(20);
        assertThat(candidates)
                .extracting(LeaderCandidateResponse::getUserId)
                .containsExactly(java.util.Arrays.stream(eligibleByNameOrder)
                        .limit(20)
                        .map(TestUser::userId)
                        .toArray(String[]::new));
        assertThat(candidates)
                .extracting(LeaderCandidateResponse::getUserId)
                .doesNotContain(inactiveAccount.userId(), inactiveRoleAccount.userId());
        assertThat(candidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.getName()).containsIgnoringCase(commonLabel);
                    assertThat(candidate.getEmail()).containsIgnoringCase(commonLabel);
                });
    }

    private ProjectResponse createProject(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String code = "D3-" + label + "-" + suffix;
        createdProjectCodes.add(code);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setCode(code);
        request.setName("D3 " + label + " " + suffix);
        return projectService.create(request, ADMIN_EMAIL);
    }

    private ProjectResponse changePrimary(Long projectId, String leaderUserId) {
        ChangeProjectLeaderRequest request = new ChangeProjectLeaderRequest();
        request.setLeaderUserId(leaderUserId);
        return projectService.changeLeader(projectId, request, ADMIN_EMAIL);
    }

    private ProjectResponse replaceLeaders(Long projectId, List<String> leaderUserIds) {
        ChangeProjectLeadersRequest request = new ChangeProjectLeadersRequest();
        request.setLeaderUserIds(leaderUserIds);
        return projectService.replaceLeaders(projectId, request, ADMIN_EMAIL);
    }

    private ProjectResponse updateLeadership(
            Long projectId,
            String primaryLeaderUserId,
            List<String> leaderUserIds
    ) {
        UpdateProjectLeadershipRequest request = new UpdateProjectLeadershipRequest();
        request.setPrimaryLeaderUserId(primaryLeaderUserId);
        request.setLeaderUserIds(leaderUserIds);
        return projectService.updateLeadership(projectId, request, ADMIN_EMAIL);
    }

    private TestUser insertMember(String label) {
        String userId = UUID.randomUUID().toString();
        String email = label + "." + userId + "@smartlab.test";
        createdUserIds.add(userId);
        Long databaseId = jdbcTemplate.queryForObject(
                """
                        insert into tbl_user (
                            user_id,
                            name,
                            email,
                            password,
                            is_active,
                            is_account_verified,
                            reset_otp_expire_at
                        ) values (?, ?, ?, ?, true, true, 0)
                        returning id
                        """,
                Long.class,
                userId,
                "D3 " + label,
                email,
                "integration-test-password"
        );
        int assignedRoles = jdbcTemplate.update(
                """
                        insert into user_roles (user_id, role_id, assigned_by)
                        select ?, id, 'd3-integration-test'
                        from roles
                        where code = 'MEMBER'
                        """,
                databaseId
        );
        assertThat(assignedRoles).isOne();
        assertThat(roleAssignments(databaseId))
                .extracting(UserRoleSnapshot::code)
                .containsExactly("MEMBER");
        return new TestUser(databaseId, userId, email);
    }

    private void assignInactiveRole(Long userDatabaseId, String suffix) {
        Long roleId = jdbcTemplate.queryForObject(
                """
                        insert into roles (code, name, is_system, is_active)
                        values (?, ?, false, false)
                        returning id
                        """,
                Long.class,
                "D3_DISABLED_" + suffix.toUpperCase(),
                "D3 disabled integration role"
        );
        int assignedRoles = jdbcTemplate.update(
                "insert into user_roles (user_id, role_id, assigned_by) values (?, ?, 'd3-integration-test')",
                userDatabaseId,
                roleId
        );
        assertThat(assignedRoles).isOne();
    }

    private Long primaryLeaderDatabaseId(Long projectId) {
        return jdbcTemplate.queryForObject(
                "select leader_user_id from projects where id = ?",
                Long.class,
                projectId
        );
    }

    private int projectMembershipCount(Long projectId) {
        Integer count = rowCount("select count(*) from project_members where project_id = ?", projectId);
        return count == null ? 0 : count;
    }

    private Long membershipId(Long projectId, Long userDatabaseId) {
        return jdbcTemplate.queryForObject(
                "select id from project_members where project_id = ? and user_id = ?",
                Long.class,
                projectId,
                userDatabaseId
        );
    }

    private int projectMemberAuditCount(Long projectId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from audit_logs audit
                        join project_members membership on audit.target_id = membership.id::text
                        where audit.target_type = 'PROJECT_MEMBER'
                          and membership.project_id = ?
                        """,
                Integer.class,
                projectId
        );
        return count == null ? 0 : count;
    }

    private ProjectMemberAuditRow latestProjectMemberAudit(Long membershipId) {
        return jdbcTemplate.queryForObject(
                """
                        select
                            action,
                            target_type,
                            target_id,
                            before_json::text,
                            after_json ->> 'projectId',
                            after_json ->> 'userId',
                            before_json ->> 'projectRole',
                            before_json ->> 'status',
                            after_json ->> 'projectRole',
                            after_json ->> 'status'
                        from audit_logs
                        where target_type = 'PROJECT_MEMBER'
                          and target_id = ?
                        order by id desc
                        limit 1
                        """,
                (resultSet, rowNumber) -> new ProjectMemberAuditRow(
                        resultSet.getString(1),
                        resultSet.getString(2),
                        resultSet.getString(3),
                        resultSet.getString(4),
                        resultSet.getString(5),
                        resultSet.getString(6),
                        resultSet.getString(7),
                        resultSet.getString(8),
                        resultSet.getString(9),
                        resultSet.getString(10)
                ),
                membershipId.toString()
        );
    }

    private Integer rowCount(String sql, Object parameter) {
        return jdbcTemplate.queryForObject(sql, Integer.class, parameter);
    }

    private List<UserRoleSnapshot> roleAssignments(Long userDatabaseId) {
        return jdbcTemplate.query(
                """
                        select ur.id, r.code, ur.assigned_by
                        from user_roles ur
                        join roles r on r.id = ur.role_id
                        where ur.user_id = ?
                        order by ur.id
                        """,
                (resultSet, rowNumber) -> new UserRoleSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("assigned_by")
                ),
                userDatabaseId
        );
    }

    private boolean hasGlobalRolePermission(Long userDatabaseId, String permissionCode) {
        return rowCount(
                """
                        select count(*)
                        from user_roles ur
                        join role_permissions rp on rp.role_id = ur.role_id
                        join permissions p on p.id = rp.permission_id
                        where ur.user_id = ? and p.code = ? and p.is_active = true
                        """,
                userDatabaseId,
                permissionCode
        ) > 0;
    }

    private String projectName(Long projectId) {
        return jdbcTemplate.queryForObject(
                "select name from projects where id = ?",
                String.class,
                projectId
        );
    }

    private void assertMembership(
            Long projectId,
            Long userDatabaseId,
            String expectedRole,
            String expectedStatus
    ) {
        String membership = jdbcTemplate.queryForObject(
                """
                        select project_role || ':' || status
                        from project_members
                        where project_id = ? and user_id = ?
                        """,
                String.class,
                projectId,
                userDatabaseId
        );
        assertThat(membership).isEqualTo(expectedRole + ":" + expectedStatus);
    }

    private Integer rowCount(String sql, Object firstParameter, Object secondParameter) {
        return jdbcTemplate.queryForObject(sql, Integer.class, firstParameter, secondParameter);
    }

    private record TestUser(Long databaseId, String userId, String email) {
    }

    private record UserRoleSnapshot(Long id, String code, String assignedBy) {
    }

    private record ProjectMemberAuditRow(
            String action,
            String targetType,
            String targetId,
            String beforeJson,
            String afterProjectId,
            String afterUserId,
            String beforeRole,
            String beforeStatus,
            String afterRole,
            String afterStatus
    ) {
    }
}
