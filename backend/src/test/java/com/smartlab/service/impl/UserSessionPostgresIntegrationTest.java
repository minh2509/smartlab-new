package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "jwt.secret.key=d1-auth-postgres-integration-secret-for-tests-only",
        "spring.jpa.hibernate.ddl-auto=none"
})
class UserSessionPostgresIntegrationTest {

    private static final Duration LOCK_OBSERVATION_TIMEOUT = Duration.ofSeconds(5);
    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private TokenHashService tokenHashService;

    @Value("${smartlab.test.target-database:smartlab_d1_auth_it}")
    private String targetDatabase;

    private final Set<Long> userIds = new LinkedHashSet<>();
    private DatabaseCounts baseline;

    @BeforeEach
    void requireExactDisposableDatabase() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        baseline = counts();
    }

    @AfterEach
    void cleanFixturesAndVerifyBaseline() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);

        for (Long userId : userIds) {
            jdbc.update("delete from user_sessions where user_id = ?", userId);
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from user_permission_overrides where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }

        userIds.clear();
        assertThat(counts()).isEqualTo(baseline);
    }

    @Test
    void loginReturnsOpaqueRefreshCredentialButDatabaseStoresOnlyHash() {
        UserFixture user = insertUser("hash-only");

        UserSessionService.SessionCredential credential =
                userSessionService.createSessionCredential(
                        user.entity(),
                        "JUnit",
                        "127.0.0.1"
                );

        String persistedHash = jdbc.queryForObject("""
                select refresh_token_hash
                from user_sessions
                where session_id = ?
                """, String.class, credential.session().getSessionId());

        assertThat(credential.refreshToken()).isNotBlank();
        assertThat(persistedHash)
                .isEqualTo(tokenHashService.sha256(credential.refreshToken()))
                .isNotEqualTo(credential.refreshToken());

        assertThat(activeSessionCount(user.id())).isEqualTo(1);
    }

    @Test
    void expiredSessionDoesNotConsumeSlotAndFourthActiveLoginRevokesOldest() {
        UserFixture user = insertUser("cap");

        insertExpiredUnrevokedSession(user.id());

        UserSessionService.SessionCredential first =
                createSession(user, "one");
        UserSessionService.SessionCredential second =
                createSession(user, "two");
        UserSessionService.SessionCredential third =
                createSession(user, "three");

        Instant base = Instant.now().minusSeconds(60);

        jdbc.update(
                "update user_sessions set created_at = ? where session_id = ?",
                Timestamp.from(base),
                first.session().getSessionId()
        );
        jdbc.update(
                "update user_sessions set created_at = ? where session_id = ?",
                Timestamp.from(base.plusSeconds(10)),
                second.session().getSessionId()
        );
        jdbc.update(
                "update user_sessions set created_at = ? where session_id = ?",
                Timestamp.from(base.plusSeconds(20)),
                third.session().getSessionId()
        );

        assertThat(activeSessionCount(user.id())).isEqualTo(3);
        assertThat(unrevokedSessionCount(user.id())).isEqualTo(4);

        UserSessionService.SessionCredential fourth =
                createSession(user, "four");

        assertThat(activeSessionCount(user.id())).isEqualTo(3);
        assertThat(unrevokedSessionCount(user.id())).isEqualTo(4);

        assertThat(revokedAt(first.session().getSessionId())).isNotNull();
        assertThat(revokedAt(second.session().getSessionId())).isNull();
        assertThat(revokedAt(third.session().getSessionId())).isNull();
        assertThat(revokedAt(fourth.session().getSessionId())).isNull();

        assertThat(expiredUnrevokedSessionCount(user.id())).isEqualTo(1);
    }

    @Test
    void concurrentLoginsSerializeOnUserRowAndNeverLeaveMoreThanThreeActiveSessions()
            throws Exception {

        UserFixture user = insertUser("concurrent-login");

        ExecutorService workers = Executors.newFixedThreadPool(6);
        CountDownLatch workersReady = new CountDownLatch(6);
        CountDownLatch startWorkers = new CountDownLatch(1);

        try (Connection gate = dataSource.getConnection()) {
            assertTargetDatabase(gate);
            gate.setAutoCommit(false);

            int gateBackendPid = backendPid(gate);
            lockUser(gate, user.id());

            List<Future<CommandOutcome>> futures = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
                final int worker = i;
                futures.add(workers.submit(() -> executeAfterBarrier(
                        () -> createSession(user, "concurrent-" + worker),
                        workersReady,
                        startWorkers
                )));
            }

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startWorkers.countDown();

            LockObservation observation = null;
            Throwable observationFailure = null;

            try {
                observation = awaitUserLockWaiters(gateBackendPid, 2);
            } catch (Throwable failure) {
                observationFailure = failure;
            } finally {
                gate.commit();
            }

            List<CommandOutcome> outcomes = new ArrayList<>();
            for (Future<CommandOutcome> future : futures) {
                outcomes.add(future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            if (observationFailure != null) {
                throw new AssertionError(
                        "Did not observe PostgreSQL user-row lock serialization",
                        observationFailure
                );
            }

            assertThat(observation.waitingBackendPids().size()).isGreaterThanOrEqualTo(2);
            assertThat(outcomes).allMatch(CommandOutcome::succeeded);

            assertThat(totalSessionCount(user.id())).isEqualTo(6);
            assertThat(activeSessionCount(user.id())).isEqualTo(3);
            assertThat(revokedSessionCount(user.id())).isEqualTo(3);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRefreshOfSameTokenAllowsAtMostOneRotation()
            throws Exception {

        UserFixture user = insertUser("concurrent-refresh");

        UserSessionService.SessionCredential initial =
                createSession(user, "initial");

        String oldRefreshToken = initial.refreshToken();

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startWorkers = new CountDownLatch(1);

        try (Connection gate = dataSource.getConnection()) {
            assertTargetDatabase(gate);
            gate.setAutoCommit(false);

            int gateBackendPid = backendPid(gate);
            lockUser(gate, user.id());

            Future<CommandOutcome> first = workers.submit(() -> executeAfterBarrier(
                    () -> userSessionService.rotateRefreshToken(oldRefreshToken),
                    workersReady,
                    startWorkers
            ));

            Future<CommandOutcome> second = workers.submit(() -> executeAfterBarrier(
                    () -> userSessionService.rotateRefreshToken(oldRefreshToken),
                    workersReady,
                    startWorkers
            ));

            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startWorkers.countDown();

            LockObservation observation = null;
            Throwable observationFailure = null;

            try {
                observation = awaitUserLockWaiters(gateBackendPid, 2);
            } catch (Throwable failure) {
                observationFailure = failure;
            } finally {
                gate.commit();
            }

            List<CommandOutcome> outcomes = List.of(
                    first.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            if (observationFailure != null) {
                throw new AssertionError(
                        "Did not observe two refresh requests waiting on the user row",
                        observationFailure
                );
            }

            assertThat(observation.waitingBackendPids()).hasSize(2);
            assertThat(outcomes).filteredOn(CommandOutcome::succeeded).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(outcome -> outcome.failure() instanceof BadCredentialsException)
                    .hasSize(1);

            UserSessionService.RefreshCredential winner =
                    (UserSessionService.RefreshCredential) outcomes.stream()
                            .filter(CommandOutcome::succeeded)
                            .findFirst()
                            .orElseThrow()
                            .value();

            assertThat(winner.session().getSessionId())
                    .isEqualTo(initial.session().getSessionId());

            assertThat(activeSessionCount(user.id())).isEqualTo(1);
            assertThat(totalSessionCount(user.id())).isEqualTo(1);

            String storedHash = jdbc.queryForObject("""
                    select refresh_token_hash
                    from user_sessions
                    where session_id = ?
                    """, String.class, initial.session().getSessionId());

            assertThat(storedHash)
                    .isEqualTo(tokenHashService.sha256(winner.refreshToken()))
                    .isNotEqualTo(tokenHashService.sha256(oldRefreshToken));

            assertThatThrownBy(
                    () -> userSessionService.rotateRefreshToken(oldRefreshToken)
            ).isInstanceOf(BadCredentialsException.class);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void revokeAllInvalidatesExistingRefreshCredential() {
        UserFixture user = insertUser("revoke-all");

        UserSessionService.SessionCredential credential =
                createSession(user, "revoke-all");

        userSessionService.revokeAllByEmail(user.email());

        assertThat(activeSessionCount(user.id())).isZero();

        assertThatThrownBy(
                () -> userSessionService.rotateRefreshToken(credential.refreshToken())
        ).isInstanceOf(BadCredentialsException.class);
    }

    private UserSessionService.SessionCredential createSession(
            UserFixture user,
            String tag
    ) {
        return userSessionService.createSessionCredential(
                user.entity(),
                "JUnit-" + tag,
                "127.0.0.1"
        );
    }

    private UserFixture insertUser(String tag) {
        String marker = UUID.randomUUID().toString();
        String userId = "d1a" + marker.replace("-", "");
        String email = "d1-auth-" + tag + "-" + marker + "@example.test";

        Long id = jdbc.queryForObject("""
                insert into tbl_user (
                    user_id,
                    name,
                    email,
                    password,
                    is_active,
                    is_account_verified
                )
                values (?, ?, ?, ?, true, true)
                returning id
                """,
                Long.class,
                userId,
                "D1 Auth " + tag,
                email,
                "integration-test-only"
        );

        assertThat(id).isNotNull();
        userIds.add(id);

        UserEntity entity = userRepository.findById(id).orElseThrow();

        return new UserFixture(id, email, entity);
    }

    private void insertExpiredUnrevokedSession(Long userId) {
        jdbc.update("""
                insert into user_sessions (
                    session_id,
                    user_id,
                    refresh_token_hash,
                    user_agent,
                    ip_address,
                    expires_at,
                    revoked_at,
                    last_seen_at
                )
                values (?, ?, ?, ?, ?, now() - interval '1 day', null, now() - interval '2 days')
                """,
                UUID.randomUUID().toString(),
                userId,
                tokenHashService.sha256("expired-" + UUID.randomUUID()),
                "expired-fixture",
                "127.0.0.1"
        );
    }

    private long activeSessionCount(Long userId) {
        return jdbc.queryForObject("""
                select count(*)
                from user_sessions
                where user_id = ?
                  and revoked_at is null
                  and expires_at > now()
                """, Long.class, userId);
    }

    private long totalSessionCount(Long userId) {
        return jdbc.queryForObject(
                "select count(*) from user_sessions where user_id = ?",
                Long.class,
                userId
        );
    }

    private long unrevokedSessionCount(Long userId) {
        return jdbc.queryForObject("""
                select count(*)
                from user_sessions
                where user_id = ?
                  and revoked_at is null
                """, Long.class, userId);
    }

    private long revokedSessionCount(Long userId) {
        return jdbc.queryForObject("""
                select count(*)
                from user_sessions
                where user_id = ?
                  and revoked_at is not null
                """, Long.class, userId);
    }

    private long expiredUnrevokedSessionCount(Long userId) {
        return jdbc.queryForObject("""
                select count(*)
                from user_sessions
                where user_id = ?
                  and revoked_at is null
                  and expires_at <= now()
                """, Long.class, userId);
    }

    private Instant revokedAt(String sessionId) {
        Timestamp timestamp = jdbc.queryForObject("""
                select revoked_at
                from user_sessions
                where session_id = ?
                """, Timestamp.class, sessionId);

        return timestamp == null ? null : timestamp.toInstant();
    }

    private CommandOutcome executeAfterBarrier(
            Callable<?> command,
            CountDownLatch workersReady,
            CountDownLatch startWorkers
    ) {
        workersReady.countDown();

        try {
            if (!startWorkers.await(5, TimeUnit.SECONDS)) {
                return CommandOutcome.failure(
                        new AssertionError("Worker start barrier timed out")
                );
            }

            return CommandOutcome.success(command.call());
        } catch (Throwable failure) {
            return CommandOutcome.failure(failure);
        }
    }

    private LockObservation awaitUserLockWaiters(
            int gateBackendPid,
            int minimumWaiters
    ) {
        long deadline =
                System.nanoTime() + LOCK_OBSERVATION_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            Set<Integer> pids = new LinkedHashSet<>(jdbc.queryForList("""
                    select pid
                    from pg_stat_activity
                    where datname = current_database()
                      and pid <> pg_backend_pid()
                      and pid <> ?
                      and state = 'active'
                      and wait_event_type = 'Lock'
                      and query ilike '%tbl_user%'
                    """,
                    Integer.class,
                    gateBackendPid
            ));

            if (pids.size() >= minimumWaiters) {
                return new LockObservation(
                        gateBackendPid,
                        Set.copyOf(pids)
                );
            }

            LockSupport.parkNanos(
                    TimeUnit.MILLISECONDS.toNanos(20)
            );
        }

        throw new AssertionError(
                "Timed out waiting for PostgreSQL tbl_user lock waiters"
        );
    }

    private static void lockUser(Connection connection, Long userId)
            throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "select id from tbl_user where id = ? for update"
        )) {
            statement.setLong(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private static int backendPid(Connection connection)
            throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "select pg_backend_pid()"
        )) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private void assertTargetDatabase(Connection connection)
            throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "select current_database()"
        )) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(targetDatabase);
            }
        }
    }

    private String currentDatabase() {
        return jdbc.queryForObject(
                "select current_database()",
                String.class
        );
    }

    private DatabaseCounts counts() {
        return new DatabaseCounts(
                jdbc.queryForObject(
                        "select count(*) from tbl_user",
                        Long.class
                ),
                jdbc.queryForObject(
                        "select count(*) from user_sessions",
                        Long.class
                )
        );
    }

    private record UserFixture(
            Long id,
            String email,
            UserEntity entity
    ) {
    }

    private record DatabaseCounts(
            long users,
            long sessions
    ) {
    }

    private record LockObservation(
            int gateBackendPid,
            Set<Integer> waitingBackendPids
    ) {
    }

    private record CommandOutcome(
            Object value,
            Throwable failure
    ) {
        private static CommandOutcome success(Object value) {
            return new CommandOutcome(value, null);
        }

        private static CommandOutcome failure(Throwable failure) {
            return new CommandOutcome(null, failure);
        }

        private boolean succeeded() {
            return failure == null;
        }
    }
}
