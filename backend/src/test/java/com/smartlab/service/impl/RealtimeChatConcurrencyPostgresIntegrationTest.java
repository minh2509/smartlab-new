package com.smartlab.service.impl;

import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ChatMessageType;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=realtime-chat-concurrency-postgres-test-secret",
        "smartlab.email-outbox.max-per-poll=0"
})
class RealtimeChatConcurrencyPostgresIntegrationTest {
    private static final int WORKERS = 20;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatService chatService;

    @Value("${smartlab.test.target-database:smartlab_chat_it}")
    private String targetDatabase;

    private final Set<Long> userIds = new java.util.LinkedHashSet<>();
    private final Set<String> conversationIds = new java.util.LinkedHashSet<>();
    private UserEntity alice;
    private UserEntity bob;
    private Authentication aliceAuthentication;
    private Authentication bobAuthentication;

    @BeforeEach
    void requireExactDisposableDatabaseAndCreateUsers() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        alice = insertUser("chat-concurrency-alice");
        bob = insertUser("chat-concurrency-bob");
        aliceAuthentication = new UsernamePasswordAuthenticationToken(
                alice.getEmail(), null, List.of());
        bobAuthentication = new UsernamePasswordAuthenticationToken(
                bob.getEmail(), null, List.of());
    }

    @AfterEach
    void removeOnlyThisTestFixtures() {
        for (Long userId : userIds) {
            jdbc.update("""
                    delete from chat_conversations
                    where created_by_user_id = ?
                       or id in (select conversation_id from chat_conversation_members where user_id = ?)
                    """, userId, userId);
        }
        for (String conversationId : conversationIds) {
            jdbc.update("delete from chat_conversations where conversation_id = ?", conversationId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from user_permission_overrides where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }
        conversationIds.clear();
        userIds.clear();
    }

    @Test
    void concurrentDirectCreationReturnsOneConversationForTheUnorderedPair() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ChatConversationResponse>> results = List.of(
                    executor.submit(task(
                            () -> chatService.createDirect(bob.getUserId(), aliceAuthentication), ready, start)),
                    executor.submit(task(
                            () -> chatService.createDirect(bob.getUserId(), aliceAuthentication), ready, start))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ChatConversationResponse first = results.get(0).get(10, TimeUnit.SECONDS);
            ChatConversationResponse second = results.get(1).get(10, TimeUnit.SECONDS);
            conversationIds.add(first.conversationId());

            assertThat(second.conversationId()).isEqualTo(first.conversationId());

            CountDownLatch reverseReady = new CountDownLatch(2);
            CountDownLatch reverseStart = new CountDownLatch(1);
            Future<ChatConversationResponse> reverseFirst = executor.submit(task(
                    () -> chatService.createDirect(alice.getUserId(), bobAuthentication),
                    reverseReady, reverseStart));
            Future<ChatConversationResponse> reverseSecond = executor.submit(task(
                    () -> chatService.createDirect(alice.getUserId(), bobAuthentication),
                    reverseReady, reverseStart));
            assertThat(reverseReady.await(5, TimeUnit.SECONDS)).isTrue();
            reverseStart.countDown();
            assertThat(reverseFirst.get(10, TimeUnit.SECONDS).conversationId())
                    .isEqualTo(first.conversationId());
            assertThat(reverseSecond.get(10, TimeUnit.SECONDS).conversationId())
                    .isEqualTo(first.conversationId());

            Long directConversationCount = jdbc.queryForObject(
                    "select count(*) from chat_conversations where direct_key = ?",
                    Long.class,
                    Math.min(alice.getId(), bob.getId()) + ":" + Math.max(alice.getId(), bob.getId()));
            assertThat(directConversationCount).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    """
                    select count(*)
                    from chat_conversation_members m
                    join chat_conversations c on c.id = m.conversation_id
                    where c.conversation_id = ? and m.left_at is null
                    """,
                    Long.class, first.conversationId())).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSendsAllocateStrictlyMonotonicConversationSequences() throws Exception {
        ChatConversationResponse conversation = chatService.createDirect(bob.getUserId(), aliceAuthentication);
        conversationIds.add(conversation.conversationId());

        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<ChatMessageResponse>> tasks = new ArrayList<>();
            for (int index = 0; index < WORKERS; index++) {
                String clientMessageId = "concurrent-" + UUID.randomUUID();
                tasks.add(task(() -> chatService.sendMessage(
                        conversation.conversationId(),
                        new ChatSendMessageRequest(
                                clientMessageId, ChatMessageType.TEXT, "message", null, null),
                        aliceAuthentication), ready, start));
            }
            List<Future<ChatMessageResponse>> results = new ArrayList<>();
            for (Callable<ChatMessageResponse> task : tasks) {
                results.add(executor.submit(task));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> sequences = new ArrayList<>();
            for (Future<ChatMessageResponse> result : results) {
                sequences.add(result.get(10, TimeUnit.SECONDS).messageSeq());
            }
            sequences.sort(Comparator.naturalOrder());

            assertThat(sequences).containsExactly(
                    1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                    11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
            assertThat(jdbc.queryForObject(
                    "select last_message_seq from chat_conversations where conversation_id = ?",
                    Long.class,
                    conversation.conversationId())).isEqualTo(20L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateClientMessageIdReturnsOnePersistedMessage() throws Exception {
        ChatConversationResponse conversation = chatService.createDirect(bob.getUserId(), aliceAuthentication);
        conversationIds.add(conversation.conversationId());
        String clientMessageId = "idempotent-" + UUID.randomUUID();
        ChatSendMessageRequest request = new ChatSendMessageRequest(
                clientMessageId, ChatMessageType.TEXT, "retry-safe message", null, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ChatMessageResponse> first = executor.submit(task(
                    () -> chatService.sendMessage(conversation.conversationId(), request, aliceAuthentication),
                    ready, start));
            Future<ChatMessageResponse> retry = executor.submit(task(
                    () -> chatService.sendMessage(conversation.conversationId(), request, aliceAuthentication),
                    ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ChatMessageResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            ChatMessageResponse retryResponse = retry.get(10, TimeUnit.SECONDS);

            assertThat(retryResponse.id()).isEqualTo(firstResponse.id());
            assertThat(retryResponse.messageSeq()).isEqualTo(firstResponse.messageSeq());
            assertThat(jdbc.queryForObject(
                    """
                    select count(*)
                    from chat_messages
                    where conversation_id = (select id from chat_conversations where conversation_id = ?)
                      and client_message_id = ?
                    """,
                    Long.class, conversation.conversationId(), clientMessageId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "select last_message_seq from chat_conversations where conversation_id = ?",
                    Long.class, conversation.conversationId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> Callable<T> task(Callable<T> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return action.call();
        };
    }

    private UserEntity insertUser(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String userId = label + "-" + suffix;
        String email = userId + "@smartlab.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, userId, label, email, "not-used");
        userIds.add(id);
        return userRepository.findById(id).orElseThrow();
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }
}
