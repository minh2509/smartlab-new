package com.smartlab.service.impl;

import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.CreateGroupChatRequest;
import com.smartlab.dto.request.ForwardChatMessageRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ChatMessageType;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.ChatService;
import com.smartlab.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private FileService fileService;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Value("${smartlab.test.target-database:smartlab_chat_it}")
    private String targetDatabase;

    private final Set<Long> userIds = new java.util.LinkedHashSet<>();
    private final Set<String> conversationIds = new java.util.LinkedHashSet<>();
    private final Set<Long> fileIds = new java.util.LinkedHashSet<>();
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
            jdbc.update("delete from member_profiles where user_id = ?", userId);
        }
        for (Long fileId : fileIds) {
            jdbc.update("delete from files where id = ?", fileId);
        }
        for (Long userId : userIds) {
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from user_permission_overrides where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }
        conversationIds.clear();
        fileIds.clear();
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

    @Test
    void forwardingIsRetrySafeAndSenderReadWatermarkAdvances() {
        ChatConversationResponse conversation = chatService.createDirect(bob.getUserId(), aliceAuthentication);
        conversationIds.add(conversation.conversationId());
        ChatMessageResponse original = chatService.sendMessage(conversation.conversationId(),
                new ChatSendMessageRequest("source-" + UUID.randomUUID(),
                        ChatMessageType.TEXT, "source", null, null), aliceAuthentication);
        ChatMessageResponse other = chatService.sendMessage(conversation.conversationId(),
                new ChatSendMessageRequest("other-" + UUID.randomUUID(),
                        ChatMessageType.TEXT, "other", null, null), aliceAuthentication);
        String clientId = "forward-" + UUID.randomUUID();
        ForwardChatMessageRequest request = new ForwardChatMessageRequest(clientId);

        ChatMessageResponse first = chatService.forwardMessage(conversation.conversationId(),
                original.id(), conversation.conversationId(), request, bobAuthentication);
        ChatMessageResponse retried = chatService.forwardMessage(conversation.conversationId(),
                original.id(), conversation.conversationId(), request, bobAuthentication);
        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(first.messageSeq()).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "select count(*) from chat_messages where conversation_id = "
                        + "(select id from chat_conversations where conversation_id = ?) "
                        + "and client_message_id = ?",
                Long.class, conversation.conversationId(), clientId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select last_read_seq from chat_conversation_members where conversation_id = "
                        + "(select id from chat_conversations where conversation_id = ?) and user_id = ?",
                Long.class, conversation.conversationId(), bob.getId())).isEqualTo(3L);
        assertThatThrownBy(() -> chatService.forwardMessage(conversation.conversationId(),
                other.id(), conversation.conversationId(), request, bobAuthentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void chatAttachmentScopeFollowsActiveConversationMembershipAndRevokesLeaver() {
        ChatConversationResponse group = chatService.createGroup(
                new CreateGroupChatRequest("Attachment isolation", List.of(bob.getUserId()), null),
                aliceAuthentication);
        conversationIds.add(group.conversationId());
        Long fileId = jdbc.queryForObject("""
                insert into files (
                    owner_user_id, storage_provider, storage_key, original_name,
                    mime_type, size_bytes, access_scope, created_at
                )
                values (?, 'TEST', ?, 'private-notes.txt', 'text/plain', 5, 'PRIVATE', now())
                returning id
                """, Long.class, alice.getId(), "chat-test-" + UUID.randomUUID());
        fileIds.add(fileId);
        Authentication unrelatedAdmin = new UsernamePasswordAuthenticationToken(
                "unrelated-admin@smartlab.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(fileService.canRead(fileId, aliceAuthentication)).isTrue();
        assertThat(fileService.canRead(fileId, bobAuthentication)).isFalse();
        ChatMessageResponse message = chatService.sendMessage(
                group.conversationId(),
                new ChatSendMessageRequest("file-" + UUID.randomUUID(),
                        ChatMessageType.FILE, null, null, List.of(fileId)),
                aliceAuthentication);

        assertThat(message.files()).hasSize(1);
        assertThat(message.files().get(0).accessScope()).isEqualTo("CHAT");
        assertThat(jdbc.queryForObject("select access_scope from files where id = ?", String.class, fileId))
                .isEqualTo("CHAT");
        assertThat(fileService.canRead(fileId, aliceAuthentication)).isTrue();
        assertThat(fileService.canRead(fileId, bobAuthentication)).isTrue();
        assertThat(fileService.canRead(fileId, unrelatedAdmin)).isFalse();

        chatService.removeMember(group.conversationId(), bob.getUserId(), bobAuthentication);

        assertThat(fileService.canRead(fileId, bobAuthentication)).isFalse();
        assertThat(fileService.canRead(fileId, aliceAuthentication)).isTrue();

        chatService.deleteMessage(group.conversationId(), message.id(), aliceAuthentication);
        assertThat(fileService.canRead(fileId, aliceAuthentication)).isFalse();
        assertThat(chatService.listMessages(group.conversationId(), null, null, 50, aliceAuthentication)
                .messages()).singleElement().satisfies(deleted -> {
                    assertThat(deleted.deletedAt()).isNotNull();
                    assertThat(deleted.files()).isEmpty();
                });
    }

    @Test
    void sharedPrivateFileIsNotReclassifiedOrAttachedToChat() {
        ChatConversationResponse conversation = chatService.createDirect(bob.getUserId(), aliceAuthentication);
        conversationIds.add(conversation.conversationId());
        Long fileId = jdbc.queryForObject("""
                insert into files (
                    owner_user_id, storage_provider, storage_key, original_name,
                    mime_type, size_bytes, access_scope, created_at
                ) values (?, 'TEST', ?, 'shared-private.txt', 'text/plain', 5, 'PRIVATE', now())
                returning id
                """, Long.class, alice.getId(), "shared-chat-test-" + UUID.randomUUID());
        fileIds.add(fileId);
        jdbc.update("""
                insert into member_profiles(id, user_id, avatar_file_id, active_status, is_featured, created_at)
                values (nextval('member_profiles_id_seq'), ?, ?, 'ACTIVE', false, now())
                """, alice.getId(), fileId);

        assertThat(storedFileRepository.hasNonChatReference(fileId)).isTrue();
        assertThatThrownBy(() -> chatService.sendMessage(conversation.conversationId(),
                new ChatSendMessageRequest("shared-" + UUID.randomUUID(),
                        ChatMessageType.FILE, null, null, List.of(fileId)), aliceAuthentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        assertThat(jdbc.queryForObject("select access_scope from files where id = ?", String.class, fileId))
                .isEqualTo("PRIVATE");
        assertThat(jdbc.queryForObject("select count(*) from chat_message_files where file_id = ?", Long.class, fileId))
                .isZero();
    }
    @Test
    void ownerExplicitlyHandsOverThenLosesAccessWhileSuccessorCanManage() {
        ChatConversationResponse group = chatService.createGroup(
                new CreateGroupChatRequest("Handover", List.of(bob.getUserId()), null), aliceAuthentication);
        conversationIds.add(group.conversationId());
        chatService.sendMessage(group.conversationId(),
                new ChatSendMessageRequest("handover-" + UUID.randomUUID(), ChatMessageType.TEXT,
                        "before leaving", null, null), aliceAuthentication);

        assertThatThrownBy(() -> chatService.leaveGroupWithSuccessor(group.conversationId(),
                alice.getUserId(), aliceAuthentication)).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> chatService.leaveGroupWithSuccessor(group.conversationId(),
                "not-a-member", aliceAuthentication)).hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> chatService.leaveGroupWithSuccessor(group.conversationId(),
                alice.getUserId(), bobAuthentication)).hasMessageContaining("403 FORBIDDEN");
        assertThat(jdbc.queryForObject("select count(*) from chat_conversation_members m "
                + "join chat_conversations c on c.id=m.conversation_id where c.conversation_id=? "
                + "and m.left_at is null and m.membership_role='OWNER'", Long.class,
                group.conversationId())).isEqualTo(1L);

        chatService.leaveGroupWithSuccessor(group.conversationId(), bob.getUserId(), aliceAuthentication);
        assertThatThrownBy(() -> chatService.listMessages(group.conversationId(), null, null, 50,
                aliceAuthentication)).hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> chatService.closeGroup(group.conversationId(), aliceAuthentication))
                .hasMessageContaining("404 NOT_FOUND");
        assertThat(chatService.listMessages(group.conversationId(), null, null, 50, bobAuthentication)
                .messages()).hasSize(1);
        assertThat(chatService.listConversations(bobAuthentication).stream()
                .filter(c -> c.conversationId().equals(group.conversationId())).findFirst().orElseThrow()
                .members()).filteredOn(m -> m.role() == com.smartlab.enums.ChatMembershipRole.OWNER)
                .singleElement().satisfies(m -> assertThat(m.userId()).isEqualTo(bob.getUserId()));
        assertThat(jdbc.queryForObject("select count(*) from chat_conversation_members m "
                + "join chat_conversations c on c.id=m.conversation_id where c.conversation_id=? "
                + "and m.left_at is null and m.membership_role='OWNER'", Long.class,
                group.conversationId())).isEqualTo(1L);
    }

    @Test
    void concurrentSuccessorExitAndOwnerHandoverNeverLeavesGroupOwnerless() throws Exception {
        ChatConversationResponse group = chatService.createGroup(
                new CreateGroupChatRequest("Transfer race", List.of(bob.getUserId()), null),
                aliceAuthentication);
        conversationIds.add(group.conversationId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> transfer = executor.submit(task(() -> {
                try {
                    chatService.leaveGroupWithSuccessor(group.conversationId(), bob.getUserId(),
                            aliceAuthentication);
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException expected) {
                    assertThat(expected.getStatusCode().value()).isEqualTo(404);
                    return false;
                }
            }, ready, start));
            Future<Boolean> exit = executor.submit(task(() -> {
                try {
                    chatService.removeMember(group.conversationId(), bob.getUserId(), bobAuthentication);
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException expected) {
                    assertThat(expected.getStatusCode().value()).isEqualTo(409);
                    return false;
                }
            }, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean transferred = transfer.get(10, TimeUnit.SECONDS);
            boolean exited = exit.get(10, TimeUnit.SECONDS);
            assertThat(transferred).isNotEqualTo(exited);
            assertThat(jdbc.queryForObject("select count(*) from chat_conversation_members m "
                    + "join chat_conversations c on c.id=m.conversation_id where c.conversation_id=? "
                    + "and m.left_at is null and m.membership_role='OWNER'", Long.class,
                    group.conversationId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dissolutionRetainsHistoryAndFilesForMembersButRejectsAllWritesAndFormerMembers() {
        UserEntity carol = insertUser("chat-former-member");
        Authentication carolAuth = new UsernamePasswordAuthenticationToken(carol.getEmail(), null, List.of());
        ChatConversationResponse group = chatService.createGroup(new CreateGroupChatRequest(
                "Closed history", List.of(bob.getUserId(), carol.getUserId()), null), aliceAuthentication);
        conversationIds.add(group.conversationId());
        Long fileId = jdbc.queryForObject("""
                insert into files (owner_user_id, storage_provider, storage_key, original_name,
                    mime_type, size_bytes, access_scope, created_at)
                values (?, 'TEST', ?, 'archive.txt', 'text/plain', 5, 'PRIVATE', now()) returning id
                """, Long.class, alice.getId(), "closed-chat-" + UUID.randomUUID());
        fileIds.add(fileId);
        ChatMessageResponse message = chatService.sendMessage(group.conversationId(),
                new ChatSendMessageRequest("archive-" + UUID.randomUUID(), ChatMessageType.FILE,
                        null, null, List.of(fileId)), aliceAuthentication);
        chatService.removeMember(group.conversationId(), carol.getUserId(), carolAuth);
        assertThatThrownBy(() -> chatService.closeGroup(group.conversationId(), bobAuthentication))
                .hasMessageContaining("403 FORBIDDEN");
        ChatConversationResponse closed = chatService.closeGroup(group.conversationId(), aliceAuthentication);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.archived()).isTrue();
        assertThat(chatService.closeGroup(group.conversationId(), aliceAuthentication).closedAt())
                .isEqualTo(closed.closedAt());
        assertThat(chatService.listMessages(group.conversationId(), null, null, 50, bobAuthentication)
                .messages()).singleElement().satisfies(m -> assertThat(m.id()).isEqualTo(message.id()));
        assertThat(chatService.searchMessages(group.conversationId(), "nonexistent", 10, bobAuthentication))
                .isEmpty();
        assertThat(fileService.canRead(fileId, bobAuthentication)).isTrue();
        assertThat(fileService.canRead(fileId, carolAuth)).isFalse();
        assertThatThrownBy(() -> chatService.listMessages(group.conversationId(), null, null, 50,
                carolAuth)).hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> chatService.sendMessage(group.conversationId(),
                new ChatSendMessageRequest("closed-" + UUID.randomUUID(), ChatMessageType.TEXT,
                        "blocked", null, null), bobAuthentication)).hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.editMessage(group.conversationId(), message.id(),
                new com.smartlab.dto.request.EditChatMessageRequest("blocked"), aliceAuthentication))
                .hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.deleteMessage(group.conversationId(), message.id(),
                aliceAuthentication)).hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.toggleReaction(group.conversationId(), message.id(),
                new com.smartlab.dto.request.ChatReactionRequest("👍"), bobAuthentication))
                .hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.addMember(group.conversationId(),
                new com.smartlab.dto.request.AddChatMemberRequest(carol.getUserId()), aliceAuthentication))
                .hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.removeMember(group.conversationId(), bob.getUserId(),
                bobAuthentication)).hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.leaveGroupWithSuccessor(group.conversationId(),
                bob.getUserId(), aliceAuthentication)).hasMessageContaining("409 CONFLICT");
        ChatConversationResponse direct = chatService.createDirect(alice.getUserId(), bobAuthentication);
        conversationIds.add(direct.conversationId());
        ChatMessageResponse directMessage = chatService.sendMessage(direct.conversationId(),
                new ChatSendMessageRequest("open-" + UUID.randomUUID(), ChatMessageType.TEXT,
                        "still open", null, null), bobAuthentication);
        assertThatThrownBy(() -> chatService.forwardMessage(group.conversationId(), message.id(),
                direct.conversationId(), new ForwardChatMessageRequest("blocked-source-" + UUID.randomUUID()),
                bobAuthentication)).hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> chatService.forwardMessage(direct.conversationId(), directMessage.id(),
                group.conversationId(), new ForwardChatMessageRequest("blocked-target-" + UUID.randomUUID()),
                bobAuthentication)).hasMessageContaining("409 CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where conversation_id="
                + "(select id from chat_conversations where conversation_id=?)", Long.class,
                group.conversationId())).isEqualTo(1L);
    }

    @Test
    void concurrentCloseAndSendCannotPersistAWriteAfterClosure() throws Exception {
        ChatConversationResponse group = chatService.createGroup(
                new CreateGroupChatRequest("Race", List.of(bob.getUserId()), null), aliceAuthentication);
        conversationIds.add(group.conversationId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> closing = executor.submit(task(() -> chatService.closeGroup(group.conversationId(),
                    aliceAuthentication), ready, start));
            Future<?> sending = executor.submit(task(() -> {
                try {
                    chatService.sendMessage(group.conversationId(), new ChatSendMessageRequest(
                            "race-" + UUID.randomUUID(), ChatMessageType.TEXT, "race", null, null),
                            bobAuthentication);
                } catch (org.springframework.web.server.ResponseStatusException expected) {
                    assertThat(expected.getStatusCode().value()).isEqualTo(409);
                }
                return null;
            }, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            closing.get(10, TimeUnit.SECONDS);
            sending.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> chatService.sendMessage(group.conversationId(),
                    new ChatSendMessageRequest("later-" + UUID.randomUUID(), ChatMessageType.TEXT,
                            "later", null, null), bobAuthentication)).hasMessageContaining("409 CONFLICT");
            assertThat(jdbc.queryForObject("select count(*) from chat_messages m "
                    + "join chat_conversations c on c.id=m.conversation_id "
                    + "where c.conversation_id=? and m.created_at > c.closed_at", Long.class,
                    group.conversationId())).isZero();
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
                insert into tbl_user (
                    user_id, name, email, password, is_active, is_account_verified,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, true, true, now(), now())
                returning id
                """, Long.class, userId, label, email, "not-used");
        userIds.add(id);
        return userRepository.findById(id).orElseThrow();
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }
}
