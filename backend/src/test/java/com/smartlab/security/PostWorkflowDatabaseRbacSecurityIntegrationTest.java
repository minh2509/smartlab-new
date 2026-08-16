package com.smartlab.security;

import com.smartlab.dto.request.ReviewPostRequest;
import com.smartlab.dto.response.PostDetailResponse;
import com.smartlab.dto.response.PostSummaryResponse;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserPermissionOverrideEntity;
import com.smartlab.entity.UserRoleEntity;
import com.smartlab.entity.UserSessionEntity;
import com.smartlab.enums.PermissionOverrideEffect;
import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import com.smartlab.enums.ReviewDecision;
import com.smartlab.repo.RoleRepository;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.UserPermissionOverrideRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserRoleRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.PostService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret.key=t08-controlled-runtime-jwt-secret-key-for-tests-only")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostWorkflowDatabaseRbacSecurityIntegrationTest {

    private static final String MARKER = "t13-rbac-acceptance";
    private static final String ADMIN_EMAIL = "t13-admin-rbac-acceptance@example.test";
    private static final String LEADER_EMAIL = "t13-leader-rbac-acceptance@example.test";
    private static final String MEMBER_EMAIL = "t13-member-rbac-acceptance@example.test";
    private static final Long POST_ID = 1313L;
    private static final Set<String> WORKFLOW_AUTHORITIES = Set.of(
            "posts.submit",
            "posts.review",
            "posts.publish",
            "posts.publish.direct"
    );

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserPermissionOverrideRepository userPermissionOverrideRepository;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private AppUserDetailService appUserDetailService;
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private PostService postService;

    @Value("${smartlab.test.target-database:smartlab_rich_editor_deploy_rehearsal}")
    private String targetDatabase;

    private final Map<String, FixtureActor> actors = new LinkedHashMap<>();
    private final List<Long> sessionIds = new ArrayList<>();
    private final List<Long> userRoleIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> overrideIds = new ArrayList<>();
    private BaselineCounts baseline;
    private PersistentRbacSnapshot persistentRbacSnapshot;

    @BeforeAll
    void createCommittedRbacSessionFixtures() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        assertThat(countFixtureUsers()).isZero();
        assertPersistedT12Policy();

        baseline = baselineCounts();
        persistentRbacSnapshot = persistentRbacSnapshot();

        try {
            actors.put("ADMIN", createActor("ADMIN", ADMIN_EMAIL));
            actors.put("LEADER", createActor("LEADER", LEADER_EMAIL));
            actors.put("MEMBER", createActor("MEMBER", MEMBER_EMAIL));
        } catch (RuntimeException | Error failure) {
            cleanExactFixtures();
            throw failure;
        }
    }

    @BeforeEach
    void resetControlledBusinessBoundary() {
        reset(postService);
    }

    @AfterAll
    void cleanFixturesAndVerifyPersistentState() {
        if (!targetDatabase.equals(currentDatabase())) {
            return;
        }

        cleanExactFixtures();

        assertThat(countFixtureUsers()).isZero();
        assertThat(baselineCounts()).isEqualTo(baseline);
        assertThat(persistentRbacSnapshot()).isEqualTo(persistentRbacSnapshot);
        assertPersistedT12Policy();
    }

    @Test
    @Order(1)
    void databaseIdentityAndPersistedT12PolicyAreExact() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        assertPersistedT12Policy();
        assertThat(baseline.permissions()).isEqualTo(21);
        assertThat(baseline.rolePermissions()).isEqualTo(42);
    }

    @Test
    @Order(2)
    void productionPermissionServiceResolvesExactRoleDerivedWorkflowAuthorities() {
        assertPermissionMatrix(actor("ADMIN"), false, true, true, true);
        assertPermissionMatrix(actor("LEADER"), true, false, false, false);
        assertPermissionMatrix(actor("MEMBER"), true, false, false, false);

        assertThat(permissionService.getEffectivePermissionCodes(actor("ADMIN").user()))
                .contains("POST_MANAGE")
                .doesNotContain("posts.submit");
    }

    @Test
    @Order(3)
    void productionUserDetailsPreservesExactPermissionsAndAddsRoleAuthorities() {
        assertUserDetailsMatrix(actor("ADMIN"), false, true, true, true, "ROLE_ADMIN");
        assertUserDetailsMatrix(actor("LEADER"), true, false, false, false, "ROLE_LEADER");
        assertUserDetailsMatrix(actor("MEMBER"), true, false, false, false, "ROLE_MEMBER");

        assertThat(authorities(actor("ADMIN").userDetails()))
                .contains("ROLE_ADMIN", "POST_MANAGE")
                .doesNotContain("posts.submit", "POSTS.SUBMIT");
    }

    @Test
    @Order(4)
    void adminJwtAllowsReviewAndPublicationsButExactSubmitAuthorityRemainsRequired() throws Exception {
        FixtureActor admin = actor("ADMIN");
        stubReview(admin.email());
        stubPublish(admin.email());
        stubDirectPublish(admin.email());

        perform(admin, "/posts/1313/submit", null)
                .andExpect(status().isForbidden());
        perform(admin, "/posts/1313/reviews", reviewBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        perform(admin, "/posts/1313/publish", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        perform(admin, "/posts/1313/direct-publish", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(postService, never()).submitForReview(admin.email(), POST_ID);
        verify(postService).reviewPost(
                admin.email(),
                POST_ID,
                new ReviewPostRequest(ReviewDecision.APPROVED, "ready")
        );
        verify(postService).publishPost(admin.email(), POST_ID);
        verify(postService).directPublishPost(admin.email(), POST_ID);
    }

    @Test
    @Order(5)
    void leaderJwtAllowsOnlySubmitWorkflowCommand() throws Exception {
        FixtureActor leader = actor("LEADER");
        stubSubmit(leader.email());

        perform(leader, "/posts/1313/submit", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        perform(leader, "/posts/1313/reviews", reviewBody())
                .andExpect(status().isForbidden());
        perform(leader, "/posts/1313/publish", null)
                .andExpect(status().isForbidden());
        perform(leader, "/posts/1313/direct-publish", null)
                .andExpect(status().isForbidden());

        verify(postService).submitForReview(leader.email(), POST_ID);
        verify(postService, never()).reviewPost(any(), any(), any());
        verify(postService, never()).publishPost(any(), any());
        verify(postService, never()).directPublishPost(any(), any());
    }

    @Test
    @Order(6)
    void memberJwtAllowsOnlySubmitWorkflowCommand() throws Exception {
        FixtureActor member = actor("MEMBER");
        stubSubmit(member.email());

        perform(member, "/posts/1313/submit", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        perform(member, "/posts/1313/reviews", reviewBody())
                .andExpect(status().isForbidden());
        perform(member, "/posts/1313/publish", null)
                .andExpect(status().isForbidden());
        perform(member, "/posts/1313/direct-publish", null)
                .andExpect(status().isForbidden());

        verify(postService).submitForReview(member.email(), POST_ID);
        verify(postService, never()).reviewPost(any(), any(), any());
        verify(postService, never()).publishPost(any(), any());
        verify(postService, never()).directPublishPost(any(), any());
    }

    @Test
    @Order(7)
    void effectiveGrantAllowsMemberToUseReviewerReadsWithoutRoleNameAuthorization() throws Exception {
        FixtureActor member = actor("MEMBER");
        UserPermissionOverrideEntity override = saveReviewOverride(member, PermissionOverrideEffect.GRANT);
        when(postService.getReviewablePosts(member.email())).thenReturn(List.of(summaryResponse()));
        when(postService.getReviewablePost(member.email(), POST_ID))
                .thenReturn(response(PostStatus.PENDING_REVIEW));

        try {
            assertThat(permissionService.getEffectivePermissionCodes(member.user())).contains("posts.review");
            performGet(member, "/posts/review-queue")
                    .andExpect(status().isOk());
            performGet(member, "/posts/review-queue/1313")
                    .andExpect(status().isOk());

            verify(postService).getReviewablePosts(member.email());
            verify(postService).getReviewablePost(member.email(), POST_ID);
        } finally {
            deleteOverride(override);
        }
    }

    @Test
    @Order(8)
    void effectiveDenyBlocksAdminReviewerReadsDespiteRoleDerivedPermission() throws Exception {
        FixtureActor admin = actor("ADMIN");
        UserPermissionOverrideEntity override = saveReviewOverride(admin, PermissionOverrideEffect.DENY);

        try {
            assertThat(permissionService.getEffectivePermissionCodes(admin.user())).doesNotContain("posts.review");
            performGet(admin, "/posts/review-queue")
                    .andExpect(status().isForbidden());
            performGet(admin, "/posts/review-queue/1313")
                    .andExpect(status().isForbidden());

            verify(postService, never()).getReviewablePosts(any());
            verify(postService, never()).getReviewablePost(any(), any());
        } finally {
            deleteOverride(override);
        }
    }

    @Test
    @Order(9)
    void unauthenticatedWorkflowRequestIsRejectedBeforeBusinessService() throws Exception {
        mockMvc.perform(post("/posts/1313/direct-publish"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService);
    }

    @Test
    @Order(10)
    void revokedRealSessionInvalidatesExistingProductionJwt() throws Exception {
        FixtureActor admin = actor("ADMIN");
        stubDirectPublish(admin.email());

        assertThat(userSessionService.isSessionActive(admin.sessionId())).isTrue();
        perform(admin, "/posts/1313/direct-publish", null)
                .andExpect(status().isOk());
        verify(postService).directPublishPost(admin.email(), POST_ID);

        userSessionService.revokeSession(admin.sessionId());
        assertThat(userSessionService.isSessionActive(admin.sessionId())).isFalse();
        assertThat(jdbc.queryForObject(
                "select revoked_at is not null from user_sessions where id = ?",
                Boolean.class,
                admin.sessionDatabaseId()
        )).isTrue();

        clearInvocations(postService);
        perform(admin, "/posts/1313/direct-publish", null)
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(postService);
    }

    private FixtureActor createActor(String roleCode, String email) {
        RoleEntity role = roleRepository.findByCode(roleCode).orElseThrow();
        String userId = "t13-" + roleCode.toLowerCase() + "-rbac-acceptance";
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .userId(userId)
                .name("T13 " + roleCode)
                .email(email)
                .password("t13-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build());
        userIds.add(user.getId());

        UserRoleEntity userRole = userRoleRepository.saveAndFlush(UserRoleEntity.builder()
                .user(user)
                .role(role)
                .assignedBy(MARKER)
                .build());
        userRoleIds.add(userRole.getId());

        UserSessionEntity session = userSessionService.createSession(user, MARKER, "127.0.0.1");
        sessionIds.add(session.getId());

        UserDetails userDetails = appUserDetailService.loadUserByUsername(email);
        String jwt = jwtUtil.generateToken(userDetails, session.getSessionId());

        assertThat(jdbc.queryForObject(
                "select count(*) from user_sessions where id = ? and user_id = ? and revoked_at is null and expires_at > now()",
                Long.class,
                session.getId(),
                user.getId()
        )).isOne();

        return new FixtureActor(
                roleCode,
                user,
                email,
                userDetails,
                session.getId(),
                session.getSessionId(),
                jwt
        );
    }

    private void assertPermissionMatrix(
            FixtureActor actor,
            boolean submit,
            boolean review,
            boolean publish,
            boolean directPublish
    ) {
        Set<String> permissions = permissionService.getEffectivePermissionCodes(actor.user());
        assertTargetMatrix(permissions, submit, review, publish, directPublish);
        assertThat(permissionService.getRoleCodes(actor.user())).containsExactly(actor.roleCode());
        assertThat(permissionService.hasInactiveAssignedRole(actor.user())).isFalse();
    }

    private void assertUserDetailsMatrix(
            FixtureActor actor,
            boolean submit,
            boolean review,
            boolean publish,
            boolean directPublish,
            String expectedRoleAuthority
    ) {
        UserDetails reloaded = appUserDetailService.loadUserByUsername(actor.email());
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getUsername()).isEqualTo(actor.email());
        assertThat(authorities(reloaded)).contains(expectedRoleAuthority);
        assertTargetMatrix(authorities(reloaded), submit, review, publish, directPublish);
    }

    private void assertTargetMatrix(
            Set<String> authorities,
            boolean submit,
            boolean review,
            boolean publish,
            boolean directPublish
    ) {
        assertThat(authorities.contains("posts.submit")).isEqualTo(submit);
        assertThat(authorities.contains("posts.review")).isEqualTo(review);
        assertThat(authorities.contains("posts.publish")).isEqualTo(publish);
        assertThat(authorities.contains("posts.publish.direct")).isEqualTo(directPublish);
        assertThat(authorities.stream()
                .filter(authority -> WORKFLOW_AUTHORITIES.contains(authority.toLowerCase()))
                .filter(authority -> !WORKFLOW_AUTHORITIES.contains(authority))
                .toList()).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            FixtureActor actor,
            String path,
            String body
    ) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.jwt());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions performGet(
            FixtureActor actor,
            String path
    ) throws Exception {
        return mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.jwt()));
    }

    private void stubSubmit(String email) {
        when(postService.submitForReview(email, POST_ID))
                .thenReturn(response(PostStatus.PENDING_REVIEW));
    }

    private void stubReview(String email) {
        when(postService.reviewPost(eq(email), eq(POST_ID), any(ReviewPostRequest.class)))
                .thenReturn(response(PostStatus.PUBLISHED));
    }

    private void stubPublish(String email) {
        when(postService.publishPost(email, POST_ID))
                .thenReturn(response(PostStatus.PUBLISHED));
    }

    private void stubDirectPublish(String email) {
        when(postService.directPublishPost(email, POST_ID))
                .thenReturn(response(PostStatus.PUBLISHED));
    }

    private PostDetailResponse response(PostStatus status) {
        return PostDetailResponse.builder()
                .id(POST_ID)
                .title("T13 Post")
                .slug("t13-post")
                .visibility(PostVisibility.LAB)
                .status(status)
                .build();
    }

    private PostSummaryResponse summaryResponse() {
        return PostSummaryResponse.builder()
                .id(POST_ID)
                .title("T13 Post")
                .slug("t13-post")
                .visibility(PostVisibility.LAB)
                .status(PostStatus.PENDING_REVIEW)
                .build();
    }

    private UserPermissionOverrideEntity saveReviewOverride(
            FixtureActor actor,
            PermissionOverrideEffect effect
    ) {
        PermissionEntity permission = permissionRepository.findByCode("posts.review").orElseThrow();
        UserPermissionOverrideEntity override = userPermissionOverrideRepository.saveAndFlush(
                UserPermissionOverrideEntity.builder()
                        .user(actor.user())
                        .permission(permission)
                        .effect(effect)
                        .changedBy(MARKER)
                        .build()
        );
        overrideIds.add(override.getId());
        return override;
    }

    private void deleteOverride(UserPermissionOverrideEntity override) {
        userPermissionOverrideRepository.deleteById(override.getId());
        userPermissionOverrideRepository.flush();
        overrideIds.remove(override.getId());
    }

    private String reviewBody() {
        return "{\"decision\":\"APPROVED\",\"reason\":\"ready\"}";
    }

    private Set<String> authorities(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private FixtureActor actor(String roleCode) {
        return actors.get(roleCode);
    }

    private void assertPersistedT12Policy() {
        assertThat(jdbc.queryForObject("""
                select count(*)
                from permissions p
                join (values
                    ('posts.submit', 'Submit Post', 'POST'),
                    ('posts.review', 'Review Post', 'POST'),
                    ('posts.publish', 'Publish Post', 'POST'),
                    ('posts.publish.direct', 'Direct Publish Post', 'POST')
                ) approved(code, name, module)
                  on p.code = approved.code
                 and p.name = approved.name
                 and p.module = approved.module
                 and p.is_active
                """, Long.class)).isEqualTo(4L);

        assertThat(jdbc.queryForObject("""
                select count(*)
                from permissions
                where lower(code) in ('posts.submit','posts.review','posts.publish','posts.publish.direct')
                  and code not in ('posts.submit','posts.review','posts.publish','posts.publish.direct')
                """, Long.class)).isZero();

        assertThat(jdbc.queryForObject("""
                select count(*)
                from role_permissions rp
                join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id
                where (r.code, p.code) in (
                    ('ADMIN','posts.review'),
                    ('ADMIN','posts.publish'),
                    ('ADMIN','posts.publish.direct'),
                    ('LEADER','posts.submit'),
                    ('MEMBER','posts.submit')
                )
                """, Long.class)).isEqualTo(5L);

        assertThat(jdbc.queryForObject("""
                select count(*)
                from role_permissions rp
                join roles r on r.id = rp.role_id
                join permissions p on p.id = rp.permission_id
                where (r.code = 'ADMIN' and p.code = 'posts.submit')
                   or (r.code in ('LEADER','MEMBER')
                       and p.code in ('posts.review','posts.publish','posts.publish.direct'))
                """, Long.class)).isZero();
    }

    private BaselineCounts baselineCounts() {
        return jdbc.queryForObject("""
                select
                    (select count(*) from tbl_user) as users,
                    (select count(*) from user_roles) as user_roles,
                    (select count(*) from user_sessions) as sessions,
                    (select count(*) from user_permission_overrides) as overrides,
                    (select count(*) from permissions) as permissions,
                    (select count(*) from role_permissions) as role_permissions
                """, (rs, rowNum) -> new BaselineCounts(
                rs.getLong("users"),
                rs.getLong("user_roles"),
                rs.getLong("sessions"),
                rs.getLong("overrides"),
                rs.getLong("permissions"),
                rs.getLong("role_permissions")
        ));
    }

    private PersistentRbacSnapshot persistentRbacSnapshot() {
        return jdbc.queryForObject("""
                select
                  (select md5(string_agg(id::text||':'||code||':'||name||':'||coalesce(module,'<null>')||':'||is_active::text,'|' order by id))
                     from permissions
                    where code in ('posts.submit','posts.review','posts.publish','posts.publish.direct')) as workflow_permissions,
                  (select md5(string_agg(rp.id::text||':'||r.code||':'||p.code,'|' order by rp.id))
                     from role_permissions rp
                     join roles r on r.id=rp.role_id
                     join permissions p on p.id=rp.permission_id
                    where (r.code,p.code) in (
                      ('ADMIN','posts.review'),('ADMIN','posts.publish'),('ADMIN','posts.publish.direct'),
                      ('LEADER','posts.submit'),('MEMBER','posts.submit'))) as workflow_mappings,
                  (select md5(string_agg(id::text||':'||code||':'||name||':'||coalesce(module,'<null>')||':'||coalesce(description,'<null>')||':'||is_active::text,'|' order by id))
                     from permissions where code='POST_MANAGE') as post_manage,
                  (select md5(string_agg(rp.id::text||':'||r.code||':'||p.code,'|' order by rp.id))
                     from role_permissions rp
                     join roles r on r.id=rp.role_id
                     join permissions p on p.id=rp.permission_id
                    where p.code='POST_MANAGE') as post_manage_mapping,
                  (select md5(string_agg(id::text||':'||code||':'||is_active::text,'|' order by id))
                     from roles where code in ('ADMIN','LEADER','MEMBER')) as roles
                """, (rs, rowNum) -> new PersistentRbacSnapshot(
                rs.getString("workflow_permissions"),
                rs.getString("workflow_mappings"),
                rs.getString("post_manage"),
                rs.getString("post_manage_mapping"),
                rs.getString("roles")
        ));
    }

    private long countFixtureUsers() {
        return jdbc.queryForObject(
                "select count(*) from tbl_user where email in (?, ?, ?)",
                Long.class,
                ADMIN_EMAIL,
                LEADER_EMAIL,
                MEMBER_EMAIL
        );
    }

    private void cleanExactFixtures() {
        if (!overrideIds.isEmpty()) {
            userPermissionOverrideRepository.deleteAllById(List.copyOf(overrideIds));
            userPermissionOverrideRepository.flush();
        }
        sessionIds.forEach(id -> jdbc.update("delete from user_sessions where id = ?", id));
        userRoleIds.forEach(id -> jdbc.update("delete from user_roles where id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        sessionIds.clear();
        userRoleIds.clear();
        userIds.clear();
        overrideIds.clear();
        actors.clear();
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private record FixtureActor(
            String roleCode,
            UserEntity user,
            String email,
            UserDetails userDetails,
            Long sessionDatabaseId,
            String sessionId,
            String jwt
    ) {
    }

    private record BaselineCounts(
            long users,
            long userRoles,
            long sessions,
            long overrides,
            long permissions,
            long rolePermissions
    ) {
    }

    private record PersistentRbacSnapshot(
            String workflowPermissions,
            String workflowMappings,
            String postManage,
            String postManageMapping,
            String roles
    ) {
    }
}
