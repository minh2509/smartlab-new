package com.smartlab.service.impl;

import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/smartlab_auth_task5_it",
        "jwt.secret.key=task-five-integration-secret-only-not-for-production",
        "smartlab.email-outbox.max-per-poll=0",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
class AuthRecoveryPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetService recovery;
    @Autowired AdminAccountService invitations;
    @Autowired TokenHashService hashes;
    @Autowired MockMvc mvc;
    @MockitoBean EmailService emails;
    @MockitoSpyBean UserSessionService sessions;
    private UserEntity user;
    private static final String OTP = "123456";

    @BeforeEach void createFixture() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("smartlab_auth_task5_it");
        String marker = UUID.randomUUID().toString();
        user = users.saveAndFlush(UserEntity.builder().userId(marker).name("Recovery fixture")
                .email(marker + "@example.test").password(encoder.encode("old-password"))
                .isActive(true).isAccountVerified(true).resetOtp(encoder.encode(OTP))
                .resetOtpExpireAt(Instant.now().plusSeconds(600)).build());
    }

    @AfterEach void cleanOwnFixture() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo("smartlab_auth_task5_it");
        if (user == null) return;
        jdbc.update("delete from account_invitations where email = ?", user.getEmail());
        jdbc.update("delete from member_profiles where user_id = ?", user.getId());
        jdbc.update("delete from user_sessions where user_id = ?", user.getId());
        jdbc.update("delete from tbl_user where id = ?", user.getId());
    }

    @Test void resetConsumesOtpAndRevokesAccessAndRefreshSession() {
        var credential = sessions.createSessionCredential(user, "test", "127.0.0.1");
        recovery.verifyResetOtp(user.getEmail(), OTP);
        recovery.resetPassword(user.getEmail(), OTP, "new-password");
        UserEntity saved = users.findById(user.getId()).orElseThrow();
        assertThat(encoder.matches("new-password", saved.getPassword())).isTrue();
        assertThat(saved.getResetOtp()).isNull();
        assertThat(sessions.isSessionActive(credential.session().getSessionId())).isFalse();
        assertThatThrownBy(() -> sessions.rotateRefreshToken(credential.refreshToken()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThatThrownBy(() -> recovery.resetPassword(user.getEmail(), OTP, "attacker-password"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void failedAttemptCounterCommitsDespiteErrorAndIsSharedAcrossEndpoints() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> recovery.verifyResetOtp(user.getEmail(), "654321"))
                    .isInstanceOf(ResponseStatusException.class);
        }
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> recovery.resetPassword(user.getEmail(), "654321", "new-password"))
                    .isInstanceOf(ResponseStatusException.class);
        }
        UserEntity saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getResetOtpFailedAttempts()).isEqualTo(5);
        assertThat(saved.getResetOtp()).isNull();
        assertThatThrownBy(() -> recovery.resetPassword(user.getEmail(), OTP, "new-password"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(encoder.matches("old-password", saved.getPassword())).isTrue();
    }

    @Test void revocationFailureRollsBackPasswordAndOtp() {
        doThrow(new IllegalStateException("revocation unavailable")).when(sessions).revokeAllByEmail(user.getEmail());
        assertThatThrownBy(() -> recovery.resetPassword(user.getEmail(), OTP, "new-password"))
                .isInstanceOf(IllegalStateException.class);
        UserEntity saved = users.findById(user.getId()).orElseThrow();
        assertThat(encoder.matches("old-password", saved.getPassword())).isTrue();
        assertThat(encoder.matches(OTP, saved.getResetOtp())).isTrue();
    }

    @Test void concurrentResetsAllowExactlyOneWinner() throws Exception {
        assertThat(race(() -> recovery.resetPassword(user.getEmail(), OTP, "new-password"))).isEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().getResetOtp()).isNull();
    }

    @Test void concurrentInvitationAcceptanceAllowsExactlyOneWinner() throws Exception {
        String token = prepareInvitation();
        assertThat(race(() -> invitations.acceptInvite(inviteRequest(token)))).isEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().getIsAccountVerified()).isTrue();
        assertThat(jdbc.queryForObject("select status from account_invitations where email = ?", String.class, user.getEmail()))
                .isEqualTo("ACCEPTED");
    }

    @Test void failedInvitationActivationRollsBackPasswordVerificationAndToken() {
        String token = prepareInvitation();
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "revocation failed"))
                .when(sessions).revokeAllByEmail(user.getEmail());
        assertThatThrownBy(() -> invitations.acceptInvite(inviteRequest(token))).isInstanceOf(ResponseStatusException.class);
        UserEntity saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getIsAccountVerified()).isFalse();
        assertThat(encoder.matches("old-password", saved.getPassword())).isTrue();
        assertThat(jdbc.queryForObject("select status from account_invitations where email = ?", String.class, user.getEmail()))
                .isEqualTo("PENDING");
    }

    @Test void publicApiIsNonEnumeratingAndValidatesPayloads() throws Exception {
        var known = mvc.perform(post("/send-reset-otp").param("email", user.getEmail())).andExpect(status().isOk()).andReturn();
        var unknown = mvc.perform(post("/send-reset-otp").param("email", "missing@example.test")).andExpect(status().isOk()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        mvc.perform(post("/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bad-email\",\"otp\":\"123456\",\"newPassword\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/send-reset-otp").param("email", "not-an-email")).andExpect(status().isBadRequest());
    }

    @Test void validOtpCannotResetAnotherEmail() throws Exception {
        mvc.perform(post("/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.test\",\"otp\":\"123456\",\"newPassword\":\"attacker-password\"}"))
                .andExpect(status().isBadRequest());
        assertThat(encoder.matches("old-password", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    private String prepareInvitation() {
        user.setIsActive(false);
        user.setIsAccountVerified(false);
        users.saveAndFlush(user);
        String token = "test-token-" + UUID.randomUUID();
        jdbc.update("insert into account_invitations(invitation_id,email,token_hash,status,expires_at,resend_count) values (?,?,?,'PENDING',now()+interval '1 hour',0)",
                UUID.randomUUID().toString(), user.getEmail(), hashes.sha256(token));
        return token;
    }

    private InvitationAcceptRequest inviteRequest(String token) {
        InvitationAcceptRequest request = new InvitationAcceptRequest();
        request.setToken(token);
        request.setPassword("new-password");
        return request;
    }

    private int race(Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> contender = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try { action.run(); return 1; }
            catch (ResponseStatusException rejected) {
                assertThat(rejected.getStatusCode().value()).isEqualTo(400);
                return 0;
            }
        };
        try {
            Future<Integer> first = executor.submit(contender);
            Future<Integer> second = executor.submit(contender);
            start.countDown();
            return first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
}
