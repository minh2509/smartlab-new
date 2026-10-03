package com.smartlab.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatEventEnvelope;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ChatMessageType;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.AppUserDetailService;
import com.smartlab.service.ChatService;
import com.smartlab.service.UserSessionService;
import com.smartlab.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "jwt.secret.key=realtime-chat-websocket-postgres-test-secret",
        "smartlab.email-outbox.max-per-poll=0"
})
class RealtimeChatWebSocketPostgresIntegrationTest {
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration NO_EVENT_TIMEOUT = Duration.ofMillis(700);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private AppUserDetailService appUserDetailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Value("${smartlab.test.target-database:smartlab_chat_it}")
    private String targetDatabase;

    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<String> sessionIds = new LinkedHashSet<>();
    private final Set<String> conversationIds = new LinkedHashSet<>();
    private final List<StompSession> stompSessions = new CopyOnWriteArrayList<>();
    private HttpClient httpClient;
    private WebSocketStompClient stompClient;
    private TestUser alice;
    private TestUser bob;
    private TestUser outsider;
    private ChatConversationResponse conversation;

    @BeforeEach
    void requireExactDisposableDatabaseAndCreateFixture() {
        assertThat(currentDatabase()).isEqualTo(targetDatabase);
        assertThat(targetDatabase).isEqualTo("smartlab_chat_it");

        alice = createUser("chat-ws-alice");
        bob = createUser("chat-ws-bob");
        outsider = createUser("chat-ws-outsider");
        conversation = chatService.createDirect(
                bob.user().getUserId(), authentication(alice.user()));
        conversationIds.add(conversation.conversationId());

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter(objectMapper));
        stompClient.start();
    }

    @AfterEach
    void removeOnlyThisTestFixtures() {
        for (StompSession session : stompSessions) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
        stompSessions.clear();
        if (stompClient != null && stompClient.isRunning()) {
            stompClient.stop();
        }
        for (String sessionId : sessionIds) {
            userSessionService.revokeSession(sessionId);
        }
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
            jdbc.update("delete from user_sessions where user_id = ?", userId);
            jdbc.update("delete from user_roles where user_id = ?", userId);
            jdbc.update("delete from user_permission_overrides where user_id = ?", userId);
            jdbc.update("delete from tbl_user where id = ?", userId);
        }
        sessionIds.clear();
        conversationIds.clear();
        userIds.clear();
    }

    @Test
    void twoAuthenticatedMembersReceiveCanonicalEventAndOutsiderDoesNot() throws Exception {
        SessionProbe aliceProbe = new SessionProbe();
        SessionProbe bobProbe = new SessionProbe();
        SessionProbe outsiderProbe = new SessionProbe();
        StompSession aliceSession = connect(alice, aliceProbe);
        StompSession bobSession = connect(bob, bobProbe);
        StompSession outsiderSession = connect(outsider, outsiderProbe);
        subscribe(aliceSession, aliceProbe);
        subscribe(bobSession, bobProbe);
        subscribe(outsiderSession, outsiderProbe);
        assertThat(simpUserRegistry.getUser(alice.user().getEmail())).isNotNull();
        assertThat(simpUserRegistry.getUser(bob.user().getEmail())).isNotNull();

        String clientMessageId = "ws-runtime-" + UUID.randomUUID();
        aliceSession.send(
                "/app/chat/" + conversation.conversationId() + "/messages",
                new ChatSendMessageRequest(
                        clientMessageId, ChatMessageType.TEXT, "runtime websocket message", null, null));

        ChatEventEnvelope aliceEvent = awaitEvent(aliceProbe, "chat.message.created");
        ChatEventEnvelope bobEvent = awaitEvent(bobProbe, "chat.message.created");
        assertCanonicalMessageEvent(aliceEvent, bobEvent, clientMessageId);

        Long persistedSeq = jdbc.queryForObject(
                "select message_seq from chat_messages where client_message_id = ?",
                Long.class,
                clientMessageId);
        assertThat(persistedSeq).isEqualTo(1L);
        assertThat(messageData(aliceEvent).path("messageSeq").asLong()).isEqualTo(persistedSeq);

        int messageCountBeforeOutsiderSend = messageCount();
        outsiderSession.send(
                "/app/chat/" + conversation.conversationId() + "/messages",
                new ChatSendMessageRequest(
                        "outsider-" + UUID.randomUUID(), ChatMessageType.TEXT, "must be rejected", null, null));
        boolean outsiderRejectedOrIgnored = outsiderProbe.error.isDone()
                || (messageCountUnchangedAfterShortWait(messageCountBeforeOutsiderSend)
                && outsiderProbe.events.poll(NO_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS) == null);
        assertThat(outsiderRejectedOrIgnored).isTrue();
        assertThat(messageCount()).isEqualTo(messageCountBeforeOutsiderSend);

        HttpResponse<String> history = request(
                "GET",
                "/chat/conversations/" + conversation.conversationId() + "/messages?limit=50",
                outsider.token(), null);
        HttpResponse<String> send = request(
                "POST",
                "/chat/conversations/" + conversation.conversationId() + "/messages",
                outsider.token(), objectMapper.writeValueAsString(new ChatSendMessageRequest(
                        "outsider-rest-" + UUID.randomUUID(), ChatMessageType.TEXT, "must be rejected", null, null)));
        assertThat(history.statusCode()).isIn(403, 404);
        assertThat(send.statusCode()).isIn(403, 404);
    }

    @Test
    void reconnectUsesAfterSeqCatchUpThenResumesRealtimeDelivery() throws Exception {
        SessionProbe aliceProbe = new SessionProbe();
        SessionProbe bobProbe = new SessionProbe();
        StompSession aliceSession = connect(alice, aliceProbe);
        StompSession bobSession = connect(bob, bobProbe);
        subscribe(aliceSession, aliceProbe);
        subscribe(bobSession, bobProbe);

        sendText(aliceSession, "before-disconnect");
        ChatEventEnvelope initial = awaitEvent(bobProbe, "chat.message.created");
        long lastReceivedSeq = messageData(initial).path("messageSeq").asLong();
        assertThat(lastReceivedSeq).isEqualTo(1L);

        bobSession.disconnect();
        stompSessions.remove(bobSession);
        for (int index = 0; index < 3; index++) {
            sendText(aliceSession, "catch-up-" + index);
            awaitEvent(aliceProbe, "chat.message.created");
        }

        HttpResponse<String> catchUp = request(
                "GET",
                "/chat/conversations/" + conversation.conversationId()
                        + "/messages?afterSeq=" + lastReceivedSeq + "&limit=50",
                bob.token(), null);
        assertThat(catchUp.statusCode()).isEqualTo(200);
        JsonNode catchUpMessages = objectMapper.readTree(catchUp.body()).path("messages");
        assertThat(catchUpMessages).hasSize(3);
        assertThat(sequenceValues(catchUpMessages)).containsExactly(2L, 3L, 4L);

        SessionProbe reconnectedBobProbe = new SessionProbe();
        StompSession reconnectedBob = connect(bob, reconnectedBobProbe);
        subscribe(reconnectedBob, reconnectedBobProbe);
        sendText(aliceSession, "after-reconnect");
        ChatEventEnvelope resumed = awaitEvent(reconnectedBobProbe, "chat.message.created");
        assertThat(messageData(resumed).path("messageSeq").asLong()).isEqualTo(5L);
        assertThat(reconnectedBobProbe.events.poll(NO_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .isNull();
    }

    @Test
    void readWatermarkAndWebSocketAuthenticationAreVerifiedAtRuntime() throws Exception {
        for (int index = 0; index < 10; index++) {
            HttpResponse<String> response = request(
                    "POST",
                    "/chat/conversations/" + conversation.conversationId() + "/messages",
                    alice.token(),
                    objectMapper.writeValueAsString(new ChatSendMessageRequest(
                            "read-runtime-" + index, ChatMessageType.TEXT, "read message", null, null)));
            assertThat(response.statusCode()).isEqualTo(201);
        }

        assertThat(request("PATCH", "/chat/conversations/" + conversation.conversationId() + "/read",
                alice.token(), "{\"lastReadSeq\":5}").statusCode()).isEqualTo(200);
        assertReadState(5L, 5L);
        assertThat(request("PATCH", "/chat/conversations/" + conversation.conversationId() + "/read",
                alice.token(), "{\"lastReadSeq\":3}").statusCode()).isEqualTo(200);
        assertReadState(5L, 5L);
        assertThat(request("PATCH", "/chat/conversations/" + conversation.conversationId() + "/read",
                alice.token(), "{\"lastReadSeq\":10}").statusCode()).isEqualTo(200);
        assertReadState(10L, 0L);
        assertThat(request("PATCH", "/chat/conversations/" + conversation.conversationId() + "/read",
                alice.token(), "{\"lastReadSeq\":11}").statusCode()).isEqualTo(400);

        SessionProbe validProbe = new SessionProbe();
        StompSession validSession = connect(alice, validProbe);
        assertThat(validSession.isConnected()).isTrue();
        subscribe(validSession, validProbe);

        expectConnectFailure("malformed-jwt");
        userSessionService.revokeSession(outsider.sessionId());
        expectConnectFailure(outsider.token());
    }

    private void assertReadState(long lastReadSeq, long unreadCount) throws Exception {
        HttpResponse<String> response = request("GET", "/chat/conversations", alice.token(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode item = objectMapper.readTree(response.body()).get(0);
        assertThat(item.path("lastMessageSeq").asLong()).isEqualTo(10L);
        assertThat(item.path("lastReadSeq").asLong()).isEqualTo(lastReadSeq);
        assertThat(item.path("unreadCount").asLong()).isEqualTo(unreadCount);
    }

    private void assertCanonicalMessageEvent(
            ChatEventEnvelope first,
            ChatEventEnvelope second,
            String clientMessageId
    ) {
        assertThat(first.event()).isEqualTo("chat.message.created");
        assertThat(first.eventId()).isNotNull();
        assertThat(first.occurredAt()).isNotNull();
        assertThat(first.conversationId()).isEqualTo(conversation.conversationId());
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.occurredAt()).isEqualTo(first.occurredAt());
        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(messageData(first).path("clientMessageId").asText()).isEqualTo(clientMessageId);
        assertThat(messageData(first).path("messageSeq").asLong()).isEqualTo(1L);
        assertThat(messageData(second).path("messageSeq").asLong()).isEqualTo(1L);
    }

    private ChatEventEnvelope awaitEvent(SessionProbe probe, String event) throws Exception {
        long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ChatEventEnvelope envelope = probe.events.poll(250, TimeUnit.MILLISECONDS);
            if (envelope == null) continue;
            if (event.equals(envelope.event())) return envelope;
        }
        throw new AssertionError("Timed out waiting for " + event + "; error=" + probe.error.getNow(null));
    }

    private JsonNode messageData(ChatEventEnvelope envelope) {
        return objectMapper.valueToTree(envelope.data());
    }

    private List<Long> sequenceValues(JsonNode messages) {
        List<Long> sequences = new ArrayList<>();
        messages.forEach(message -> sequences.add(message.path("messageSeq").asLong()));
        return sequences;
    }

    private void sendText(StompSession session, String content) {
        session.send(
                "/app/chat/" + conversation.conversationId() + "/messages",
                new ChatSendMessageRequest(
                        "stomp-" + UUID.randomUUID(), ChatMessageType.TEXT, content, null, null));
    }

    private StompSession connect(TestUser user, SessionProbe probe) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + user.token());
        StompSession session = stompClient.connectAsync(
                        websocketUrl(), new WebSocketHttpHeaders(), headers, probe)
                .get(10, TimeUnit.SECONDS);
        stompSessions.add(session);
        return session;
    }

    private void expectConnectFailure(String token) {
        SessionProbe probe = new SessionProbe();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        assertThatThrownBy(() -> stompClient.connectAsync(
                        websocketUrl(), new WebSocketHttpHeaders(), headers, probe)
                .get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private void subscribe(StompSession session, SessionProbe probe) throws InterruptedException {
        session.subscribe("/user/queue/chat", new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return ChatEventEnvelope.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ChatEventEnvelope envelope) {
                    probe.events.add(envelope);
                }
            }
        });
        Thread.sleep(250);
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1.0" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return httpClient.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean messageCountUnchangedAfterShortWait(int expected) throws InterruptedException {
        Thread.sleep(NO_EVENT_TIMEOUT.toMillis());
        return messageCount() == expected;
    }

    private int messageCount() {
        return jdbc.queryForObject("""
                select count(*)
                from chat_messages m
                join chat_conversations c on c.id = m.conversation_id
                where c.conversation_id = ?
                """, Integer.class, conversation.conversationId());
    }

    private String websocketUrl() {
        return "ws://localhost:" + port + "/api/v1.0/ws/chat";
    }

    private TestUser createUser(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String userId = label + "-" + suffix;
        String email = userId + "@smartlab.test";
        Long id = jdbc.queryForObject("""
                insert into tbl_user (user_id, name, email, password, is_active, is_account_verified)
                values (?, ?, ?, ?, true, true)
                returning id
                """, Long.class, userId, label, email, "not-used");
        userIds.add(id);
        UserEntity user = userRepository.findById(id).orElseThrow();
        UserDetails details = appUserDetailService.loadUserByUsername(user.getEmail());
        UserSessionService.SessionCredential credential = userSessionService.createSessionCredential(
                user, "realtime-chat-it", "127.0.0.1");
        String sessionId = credential.session().getSessionId();
        sessionIds.add(sessionId);
        return new TestUser(user, sessionId, jwtUtil.generateToken(details, sessionId));
    }

    private Authentication authentication(UserEntity user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of());
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    private record TestUser(UserEntity user, String sessionId, String token) {
    }

    private static final class SessionProbe extends StompSessionHandlerAdapter {
        private final BlockingQueue<ChatEventEnvelope> events = new LinkedBlockingQueue<>();
        private final CompletableFuture<Throwable> error = new CompletableFuture<>();

        @Override
        public void handleException(
                StompSession session,
                StompCommand command,
                StompHeaders headers,
                byte[] payload,
                Throwable exception
        ) {
            error.complete(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            error.complete(exception);
        }
    }
}
