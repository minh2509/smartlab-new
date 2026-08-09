package com.smartlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartlab.entity.PostEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.PostRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PostSecurityFilterChainIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";
    private static final String R15_EMAIL_PATTERN = "r15-%@example.test";

    @Autowired
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private AppUserDetailService appUserDetailService;
    @MockitoBean
    private UserSessionService userSessionService;
    @MockitoBean
    private PermissionService permissionService;
    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void requireIsolatedDatabaseAndCleanFixtures() {
        assumeTrue(TARGET_DATABASE.equals(currentDatabase()),
                "R15 security integration tests require the isolated PostgreSQL database");
        cleanupR15Data();
        when(userSessionService.isSessionActive(anyString())).thenReturn(true);
        when(permissionService.getEffectivePermissionCodes(any(UserEntity.class))).thenReturn(Set.of());
        when(permissionService.getRoleCodes(any(UserEntity.class))).thenReturn(Set.of());
        when(permissionService.hasInactiveAssignedRole(any(UserEntity.class))).thenReturn(false);
    }

    @AfterEach
    void cleanFixtures() {
        if (TARGET_DATABASE.equals(currentDatabase())) {
            cleanupR15Data();
        }
    }

    @Test
    void missingTokenIsRejectedBeforeReadAndWriteRequestValidation() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/posts/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/posts/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenReturnsUnauthorizedWithoutCreatingPost() throws Exception {
        String title = uniqueTitle("Invalid token");

        mockMvc.perform(post("/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.invalid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title)))
                .andExpect(status().isUnauthorized());

        assertThat(countPostsWithTitle(title)).isZero();
    }

    @Test
    void validTokenUsesJwtEmailAsAuthenticationNameWithoutPostManage() throws Exception {
        AuthenticatedUser actor = activeUser("identity");
        assertThat(actor.userDetails().getAuthorities()).isEmpty();

        mockMvc.perform(get("/posts").header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isOk());

        verify(userSessionService).touchSession(actor.sessionId());
        assertThat(actor.userDetails().getUsername()).isEqualTo(actor.email());
    }

    @Test
    void validJwtCreatesPostForCanonicalUserWithoutPostManage() throws Exception {
        AuthenticatedUser actor = activeUser("create");
        String title = uniqueTitle("Create");

        CreatedPost response = createPost(actor, title);

        PostEntity persisted = postRepository.findById(response.id()).orElseThrow();
        assertThat(actor.userDetails().getAuthorities()).isEmpty();
        assertThat(persisted.getAuthorUserId()).isEqualTo(actor.user().getId());
        assertThat(persisted.getTitle()).isEqualTo(title);
        assertThat(persisted.getStatus().name()).isEqualTo("DRAFT");
        assertThat(persisted.getContentJson()).containsEntry("type", "doc");
        assertThat(((Map<?, ?>) persisted.getContentJson().get("nested")).get("enabled"))
                .isEqualTo(true);
    }

    @Test
    void validJwtReadsOwnedDraftThroughListAndDetailEndpoints() throws Exception {
        AuthenticatedUser actor = activeUser("read");
        CreatedPost created = createPost(actor, uniqueTitle("Read"));

        MvcResult listResult = mockMvc.perform(get("/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(list).anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(created.id()));

        MvcResult detailResult = mockMvc.perform(get("/posts/{slug}", created.slug())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        assertThat(detail.get("contentJson").isObject()).isTrue();
        assertThat(detail.get("contentJson").get("nested").get("enabled").asBoolean()).isTrue();
    }

    @Test
    void validJwtPatchesOwnedDraftAndPersistsChange() throws Exception {
        AuthenticatedUser actor = activeUser("patch");
        CreatedPost created = createPost(actor, uniqueTitle("Patch source"));
        Map<String, Object> replacement = Map.of(
                "type", "doc",
                "nested", Map.of("enabled", false),
                "items", java.util.List.of(2, "patched", true)
        );

        mockMvc.perform(patch("/posts/{id}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contentJson", replacement))))
                .andExpect(status().isOk());

        assertThat(postRepository.findById(created.id()).orElseThrow().getContentJson()).isEqualTo(replacement);
    }

    @Test
    void topLevelNonObjectContentJsonReturnsBadRequestThroughJackson3Mvc() throws Exception {
        AuthenticatedUser actor = activeUser("json-shape");
        for (String invalidContent : java.util.List.of("[]", "\"text\"", "1", "true")) {
            mockMvc.perform(post("/posts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + uniqueTitle("Invalid shape")
                                    + "\",\"contentJson\":" + invalidContent + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validJwtSoftDeletesOwnedDraftWithoutPhysicalRemoval() throws Exception {
        AuthenticatedUser actor = activeUser("delete");
        CreatedPost created = createPost(actor, uniqueTitle("Delete"));

        mockMvc.perform(delete("/posts/{id}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isNoContent());

        PostEntity persisted = postRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getDeletedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getDeletedAt());
        assertThat(postRepository.findActiveById(created.id())).isEmpty();
    }

    @Test
    void authenticatedRequestsExposeServiceForbiddenNotFoundAndConflictStatuses() throws Exception {
        AuthenticatedUser owner = activeUser("business-owner");
        AuthenticatedUser other = activeUser("business-other");
        CreatedPost otherDraft = createPost(other, uniqueTitle("Other draft"));

        mockMvc.perform(patch("/posts/{id}", otherDraft.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", uniqueTitle("Forbidden")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/posts/{slug}", otherDraft.slug())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());

        CreatedPost ownedDraft = createPost(owner, uniqueTitle("Non draft"));
        jdbc.update("update posts set status='APPROVED' where id=?", ownedDraft.id());
        mockMvc.perform(patch("/posts/{id}", ownedDraft.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", uniqueTitle("Conflict")))))
                .andExpect(status().isConflict());
    }

    @Test
    void inactiveCanonicalUserIsRejectedByApplicationAfterJwtAuthentication() throws Exception {
        AuthenticatedUser actor = activeUser("inactive");
        actor.user().setIsActive(false);
        userRepository.saveAndFlush(actor.user());

        mockMvc.perform(get("/posts").header(HttpHeaders.AUTHORIZATION, bearer(actor)))
                .andExpect(status().isUnauthorized());

        verify(userSessionService).touchSession(actor.sessionId());
    }

    private AuthenticatedUser activeUser(String tag) {
        String token = UUID.randomUUID().toString();
        String email = "r15-" + tag + "-" + token + "@example.test";
        UserEntity user = UserEntity.builder()
                .userId("r15" + token.replace("-", ""))
                .name("R15 " + tag)
                .email(email)
                .password("r15-integration-only")
                .isActive(true)
                .isAccountVerified(true)
                .resetOtpExpireAt(0L)
                .build();
        UserEntity saved = userRepository.saveAndFlush(user);
        UserDetails userDetails = appUserDetailService.loadUserByUsername(email);
        String sessionId = UUID.randomUUID().toString();
        String jwt = jwtUtil.generateToken(userDetails, sessionId);
        return new AuthenticatedUser(saved, email, userDetails, sessionId, jwt);
    }

    private CreatedPost createPost(AuthenticatedUser actor, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("contentJson").isObject()).isTrue();
        assertThat(response.get("contentJson").get("type").asText()).isEqualTo("doc");
        assertThat(response.get("contentJson").get("nested").get("enabled").asBoolean()).isTrue();
        assertThat(response.get("contentJson").get("items").isArray()).isTrue();
        return new CreatedPost(response.get("id").asLong(), response.get("slug").asText());
    }

    private String createBody(String title) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "excerpt", "R15 security integration",
                "contentJson", Map.of(
                        "type", "doc",
                        "nested", Map.of("enabled", true),
                        "items", java.util.List.of(1, "x", false)
                ),
                "visibility", "LAB"
        ));
    }

    private String bearer(AuthenticatedUser actor) {
        return "Bearer " + actor.jwt();
    }

    private long countPostsWithTitle(String title) {
        return jdbc.queryForObject("select count(*) from posts where title=?", Long.class, title);
    }

    private String uniqueTitle(String tag) {
        return "R15 " + tag + " " + UUID.randomUUID();
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private void cleanupR15Data() {
        jdbc.update("delete from posts where title like 'R15 %' or slug like 'r15-%'");
        jdbc.update("delete from tbl_user where email like ?", R15_EMAIL_PATTERN);
    }

    private record AuthenticatedUser(
            UserEntity user,
            String email,
            UserDetails userDetails,
            String sessionId,
            String jwt
    ) {
    }

    private record CreatedPost(Long id, String slug) {
    }
}
