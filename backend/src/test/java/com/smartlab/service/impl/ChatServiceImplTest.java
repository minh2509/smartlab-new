package com.smartlab.service.impl;

import com.smartlab.dto.request.AddChatMemberRequest;
import com.smartlab.dto.request.ChangeChatMemberRoleRequest;
import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.CreateGroupChatRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.EditChatMessageRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMessagePageResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.entity.ChatConversationEntity;
import com.smartlab.entity.ChatConversationMemberEntity;
import com.smartlab.entity.ChatMessageFileEntity;
import com.smartlab.entity.ChatMessageEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ChatMembershipRole;
import com.smartlab.enums.ChatMessageType;
import com.smartlab.repo.ChatConversationMemberRepository;
import com.smartlab.repo.ChatConversationRepository;
import com.smartlab.repo.ChatMessageFileRepository;
import com.smartlab.repo.ChatMessageRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.ChatRealtimePublisher;
import com.smartlab.service.FileService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
    @Mock private ChatConversationRepository conversations;
    @Mock private ChatConversationMemberRepository members;
    @Mock private ChatMessageRepository messages;
    @Mock private ChatMessageFileRepository messageFiles;
    @Mock private UserRepository users;
    @Mock private ProjectRepository projects;
    @Mock private StoredFileRepository files;
    @Mock private FileService fileService;
    @Mock private ProjectAccessService projectAccess;
    @Mock private PermissionService permissions;
    @Mock private ChatRealtimePublisher publisher;
    @Mock private Authentication authentication;

    private ChatServiceImpl service;
    private UserEntity alice;
    private UserEntity bob;
    private ChatConversationEntity conversation;
    private ChatConversationMemberEntity aliceMember;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(
                conversations, members, messages, messageFiles, users, projects, files,
                fileService, projectAccess, permissions, publisher
        );
        alice = user(1L, "alice", "alice@example.test");
        bob = user(2L, "bob", "bob@example.test");
        conversation = ChatConversationEntity.createDirect(alice, bob, alice);
        ReflectionTestUtils.setField(conversation, "id", 10L);
        ReflectionTestUtils.setField(conversation, "conversationId", "conversation-1");
        aliceMember = ChatConversationMemberEntity.create(conversation, alice, ChatMembershipRole.MEMBER);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(alice.getEmail());
        lenient().when(projectAccess.requireAuthenticatedUser(alice.getEmail())).thenReturn(alice);
        lenient().when(permissions.hasInactiveAssignedRole(any(UserEntity.class))).thenReturn(false);
        lenient().when(messages.findLatest(anyLong(), any())).thenReturn(List.of());
        lenient().when(messageFiles.findByMessageIdOrderByPositionAscIdAsc(any())).thenReturn(List.of());
    }

    @Test
    void conversationListBatchesMembersAndLatestMessagesWithoutChangingDisplayOrder() {
        UserEntity charlie = user(3L, "charlie", "charlie@example.test");
        ChatConversationEntity second = ChatConversationEntity.createDirect(alice, charlie, alice);
        ReflectionTestUtils.setField(second, "id", 11L);
        ReflectionTestUtils.setField(second, "conversationId", "conversation-2");
        ChatConversationMemberEntity bobMember = ChatConversationMemberEntity.create(
                conversation, bob, ChatMembershipRole.MEMBER);
        ChatConversationMemberEntity secondAlice = ChatConversationMemberEntity.create(
                second, alice, ChatMembershipRole.MEMBER);
        ChatConversationMemberEntity charlieMember = ChatConversationMemberEntity.create(
                second, charlie, ChatMembershipRole.MEMBER);
        ChatMessageEntity latest = ChatMessageEntity.create(conversation,
                conversation.nextMessageSequence(), bob, "list-latest", ChatMessageType.TEXT,
                "Latest message", null, null, null);
        ReflectionTestUtils.setField(latest, "id", 55L);
        when(members.findActiveByUserId(alice.getId())).thenReturn(List.of(aliceMember, secondAlice));
        when(members.findActiveMembersByConversationIds(List.of(10L, 11L)))
                .thenReturn(List.of(aliceMember, bobMember, secondAlice, charlieMember));
        when(messages.findLatestByConversationIds(List.of(10L, 11L))).thenReturn(List.of(latest));
        when(messageFiles.findByMessageIds(List.of(55L))).thenReturn(List.of());

        List<ChatConversationResponse> result = service.listConversations(authentication);

        assertThat(result).extracting(ChatConversationResponse::conversationId)
                .containsExactly("conversation-1", "conversation-2");
        assertThat(result.get(0).displayName()).isEqualTo("bob");
        assertThat(result.get(0).lastMessage().content()).isEqualTo("Latest message");
        assertThat(result.get(1).displayName()).isEqualTo("charlie");
        assertThat(result.get(1).lastMessage()).isNull();
        verify(members, never()).findActiveMembers(anyString());
        verify(messages, never()).findLatest(anyLong(), any());
    }

    @Test
    void directCreationIsIdempotentAndUsesUnorderedPairKey() {
        when(users.findAllByUserIdInForUpdate(anyCollection())).thenReturn(List.of(alice, bob));
        when(conversations.findByDirectKey("1:2")).thenReturn(Optional.empty());
        when(conversations.saveAndFlush(any())).thenAnswer(invocation -> {
            ChatConversationEntity saved = invocation.getArgument(0);
            conversation = saved;
            return saved;
        });
        when(members.findActiveMembers(any())).thenReturn(List.of(aliceMember));
        when(members.findMembership(any(), eq(alice.getId())))
                .thenReturn(Optional.of(aliceMember));
        when(members.findMembership(any(), eq(bob.getId())))
                .thenReturn(Optional.of(ChatConversationMemberEntity.create(
                        conversation, bob, ChatMembershipRole.MEMBER)));

        var first = service.createDirect(bob.getUserId(), authentication);
        when(conversations.findByDirectKey("1:2")).thenReturn(Optional.of(conversation));
        var second = service.createDirect(bob.getUserId(), authentication);

        assertThat(first.conversationId()).isEqualTo(second.conversationId());
        verify(conversations).saveAndFlush(any(ChatConversationEntity.class));
        verify(members).saveAll(any());
    }

    @Test
    void outsiderCannotReadConversation() {
        when(conversations.findByConversationId("conversation-1")).thenReturn(Optional.of(conversation));
        when(members.findMembership("conversation-1", alice.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMessages("conversation-1", null, 0L, 50, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(messages, never()).findCatchUpAfter(anyLong(), anyLong(), any());
    }

    @Test
    void duplicateClientMessageIdReturnsExistingMessageWithoutInsert() {
        ChatMessageEntity existing = ChatMessageEntity.create(
                conversation, 1L, alice, "client-1", ChatMessageType.TEXT, "hello", null);
        ReflectionTestUtils.setField(existing, "id", 101L);
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        when(messages.findBySender_IdAndClientMessageId(alice.getId(), "client-1"))
                .thenReturn(Optional.of(existing));

        ChatMessageResponse response = service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("client-1", ChatMessageType.TEXT, "hello", null, null),
                authentication
        );

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.content()).isEqualTo("hello");
        assertThatThrownBy(() -> service.sendMessage(conversation.getConversationId(),
                new ChatSendMessageRequest("client-1", ChatMessageType.TEXT, "different", null, null),
                authentication)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void groupMemberCanSendAndSequenceAdvancesBeforePersistence() {
        ChatConversationMemberEntity bobMember = ChatConversationMemberEntity.create(
                conversation, bob, ChatMembershipRole.MEMBER);
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        when(members.findActiveMembers(anyString())).thenReturn(List.of(aliceMember, bobMember));
        when(messages.findBySender_IdAndClientMessageId(alice.getId(), "client-2"))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(ChatMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageResponse response = service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("client-2", ChatMessageType.TEXT, "hello", null, null),
                authentication
        );

        assertThat(response.messageSeq()).isEqualTo(1L);
        assertThat(conversation.getLastMessageSeq()).isEqualTo(1L);
        ArgumentCaptor<ChatMessageEntity> savedMessage = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messages).saveAndFlush(savedMessage.capture());
        assertThat(savedMessage.getValue().getOriginalPayloadFingerprint()).hasSize(64);

        ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(publisher).publishAfterCommit(
                eq("chat.message.created"),
                eq(conversation.getConversationId()),
                any(ChatMessageResponse.class),
                recipients.capture()
        );
        assertThat(recipients.getValue())
                .containsExactlyInAnyOrder(alice.getEmail(), bob.getEmail());
    }

    @Test
    void removedMemberCannotSend() {
        aliceMember.leave();
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));

        assertThatThrownBy(() -> service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("client-3", ChatMessageType.TEXT, "hello", null, null),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void forwardChecksSourceMembershipOnlyAfterBothConversationLocks() {
        ChatConversationEntity target = ChatConversationEntity.createDirect(alice, bob, alice);
        ReflectionTestUtils.setField(target, "id", 11L);
        ReflectionTestUtils.setField(target, "conversationId", "target-conversation");
        when(conversations.findByConversationIdForUpdate("conversation-1"))
                .thenReturn(Optional.of(conversation));
        when(conversations.findByConversationIdForUpdate("target-conversation"))
                .thenReturn(Optional.of(target));
        when(members.findMembership("conversation-1", alice.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forwardMessage("conversation-1", 101L,
                "target-conversation", new com.smartlab.dto.request.ForwardChatMessageRequest("forward-1"),
                authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        var order = inOrder(conversations, members);
        order.verify(conversations).findByConversationIdForUpdate("conversation-1");
        order.verify(conversations).findByConversationIdForUpdate("target-conversation");
        order.verify(members).findMembership("conversation-1", alice.getId());
        verify(messages, never()).findById(anyLong());
    }

    @Test
    void readWatermarkOnlyMovesForwardAndCannotExceedLatestSequence() {
        ReflectionTestUtils.setField(conversation, "lastMessageSeq", 10L);
        ReflectionTestUtils.setField(aliceMember, "lastReadSeq", 5L);
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));

        var stale = service.markRead(conversation.getConversationId(), new ChatReadRequest(3), authentication);
        assertThat(stale.lastReadSeq()).isEqualTo(5L);
        verify(members, never()).saveAndFlush(any());

        var forward = service.markRead(conversation.getConversationId(), new ChatReadRequest(7), authentication);
        assertThat(forward.lastReadSeq()).isEqualTo(7L);
        assertThat(aliceMember.getLastReadSeq()).isEqualTo(7L);

        assertThatThrownBy(() -> service.markRead(
                conversation.getConversationId(), new ChatReadRequest(11), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void cursorHistoryIsAscendingAndCatchUpIsStrictlyAfterCursor() {
        when(conversations.findByConversationId(conversation.getConversationId())).thenReturn(Optional.of(conversation));
        when(members.findMembership(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        ChatMessageEntity five = message(5L, "five");
        ChatMessageEntity four = message(4L, "four");
        when(messages.findHistoryBefore(eq(conversation.getId()), eq(6L), any())).thenReturn(List.of(five, four));
        when(messages.existsByConversation_IdAndMessageSeqLessThan(conversation.getId(), 4L)).thenReturn(true);
        ChatMessagePageResponse history = service.listMessages(
                conversation.getConversationId(), 6L, null, 50, authentication);
        assertThat(history.messages()).extracting(ChatMessageResponse::messageSeq).containsExactly(4L, 5L);
        assertThat(history.nextBeforeSeq()).isEqualTo(4L);

        ChatMessageEntity six = message(6L, "six");
        ChatMessageEntity seven = message(7L, "seven");
        when(messages.findCatchUpAfter(eq(conversation.getId()), eq(5L), any())).thenReturn(List.of(six, seven));
        when(messages.existsByConversation_IdAndMessageSeqGreaterThan(conversation.getId(), 7L)).thenReturn(false);
        ChatMessagePageResponse catchUp = service.listMessages(
                conversation.getConversationId(), null, 5L, 50, authentication);
        assertThat(catchUp.messages()).extracting(ChatMessageResponse::messageSeq).containsExactly(6L, 7L);
        assertThat(catchUp.nextAfterSeq()).isNull();
    }

    @Test
    void unauthorizedAttachmentIsRejectedAndAuthorizedAttachmentIsPersisted() {
        StoredFileEntity file = StoredFileEntity.builder()
                .id(55L).originalName("report.pdf").mimeType("application/pdf")
                .sizeBytes(10L).accessScope("PRIVATE").storageProvider("TEST")
                .storageKey("report").ownerUser(alice).build();
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        when(messages.findBySender_IdAndClientMessageId(anyLong(), eq("file-1")))
                .thenReturn(Optional.empty());
        when(files.findByIdAndDeletedAtIsNull(55L)).thenReturn(Optional.of(file));
        when(fileService.canRead(55L, authentication)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("file-1", ChatMessageType.FILE, null, null, List.of(55L)),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        when(fileService.canRead(55L, authentication)).thenReturn(true);
        when(files.hasNonChatReference(55L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("file-shared", ChatMessageType.FILE, null, null, List.of(55L)),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(file.getAccessScope()).isEqualTo("PRIVATE");
        when(messages.saveAndFlush(any(ChatMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ChatMessageResponse response = service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("file-2", ChatMessageType.FILE, null, null, List.of(55L)),
                authentication
        );
        assertThat(response.messageType()).isEqualTo(ChatMessageType.FILE);
        assertThat(file.getAccessScope()).isEqualTo("CHAT");
        verify(files).saveAll(List.of(file));
        verify(messageFiles).save(any());
    }

    @Test
    void regularMemberCanLeaveGroupButOwnerCannotLeaveWithoutTransferFeature() {
        ChatConversationEntity group = groupConversation();
        ChatConversationMemberEntity bobMember = ChatConversationMemberEntity.create(
                group, bob, ChatMembershipRole.MEMBER);
        authenticateAs(bob);
        when(conversations.findByConversationIdForUpdate(group.getConversationId())).thenReturn(Optional.of(group));
        when(members.findMembershipForUpdate(group.getConversationId(), bob.getId()))
                .thenReturn(Optional.of(bobMember));
        when(users.findByUserId(bob.getUserId())).thenReturn(Optional.of(bob));
        when(members.findActiveMembers(group.getConversationId())).thenReturn(List.of());

        service.removeMember(group.getConversationId(), bob.getUserId(), authentication);

        assertThat(bobMember.isActive()).isFalse();

        ChatConversationMemberEntity owner = ChatConversationMemberEntity.create(
                group, alice, ChatMembershipRole.OWNER);
        authenticateAs(alice);
        when(members.findMembershipForUpdate(group.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(owner));
        when(users.findByUserId(alice.getUserId())).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> service.removeMember(
                group.getConversationId(), alice.getUserId(), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).contains("owner cannot leave");
                });
    }

    @Test
    void groupAdminCannotRemovePeerAdminOrChangeMemberRoles() {
        UserEntity carol = user(3L, "carol", "carol@example.test");
        ChatConversationEntity group = groupConversation();
        ChatConversationMemberEntity bobAdmin = ChatConversationMemberEntity.create(
                group, bob, ChatMembershipRole.ADMIN);
        ChatConversationMemberEntity carolAdmin = ChatConversationMemberEntity.create(
                group, carol, ChatMembershipRole.ADMIN);
        authenticateAs(bob);
        when(conversations.findByConversationIdForUpdate(group.getConversationId())).thenReturn(Optional.of(group));
        when(members.findMembershipForUpdate(group.getConversationId(), bob.getId()))
                .thenReturn(Optional.of(bobAdmin));
        when(members.findMembershipForUpdate(group.getConversationId(), carol.getId()))
                .thenReturn(Optional.of(carolAdmin));
        when(users.findByUserId(carol.getUserId())).thenReturn(Optional.of(carol));

        assertThatThrownBy(() -> service.removeMember(
                group.getConversationId(), carol.getUserId(), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.changeMemberRole(
                group.getConversationId(), carol.getUserId(),
                new ChangeChatMemberRoleRequest(ChatMembershipRole.MEMBER), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void groupManagerMayDeleteButNeverEditAnotherUsersMessage() {
        ChatConversationEntity group = groupConversation();
        ChatConversationMemberEntity owner = ChatConversationMemberEntity.create(
                group, alice, ChatMembershipRole.OWNER);
        ChatMessageEntity message = ChatMessageEntity.create(
                group, 1L, bob, "bob-message", ChatMessageType.TEXT, "original", null);
        ReflectionTestUtils.setField(message, "id", 201L);
        when(conversations.findByConversationIdForUpdate(group.getConversationId())).thenReturn(Optional.of(group));
        when(members.findMembershipForUpdate(group.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(owner));
        when(messages.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.editMessage(
                group.getConversationId(), message.getId(),
                new EditChatMessageRequest("tampered"), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(message.getContent()).isEqualTo("original");

        service.deleteMessage(group.getConversationId(), message.getId(), authentication);

        assertThat(message.getDeletedAt()).isNotNull();
        verify(messages).saveAndFlush(message);
    }

    @Test
    void groupCapIncludesCreatorAndRejectsAnAdditionalActiveMember() {
        List<String> hundredInvitees = IntStream.range(0, 100).mapToObj(i -> "member-" + i).toList();
        assertThatThrownBy(() -> service.createGroup(
                new CreateGroupChatRequest("Full", hundredInvitees, null), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(conversations, never()).saveAndFlush(any());

        ChatConversationEntity group = groupConversation();
        ChatConversationMemberEntity owner = ChatConversationMemberEntity.create(group, alice, ChatMembershipRole.OWNER);
        when(conversations.findByConversationIdForUpdate(group.getConversationId())).thenReturn(Optional.of(group));
        when(members.findMembershipForUpdate(group.getConversationId(), alice.getId())).thenReturn(Optional.of(owner));
        when(users.findByUserId(bob.getUserId())).thenReturn(Optional.of(bob));
        when(members.findActiveMembers(group.getConversationId()))
                .thenReturn(Collections.nCopies(100, owner), Collections.nCopies(99, owner));

        assertThatThrownBy(() -> service.addMember(group.getConversationId(),
                new AddChatMemberRequest(bob.getUserId()), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(members, never()).saveAndFlush(any());

        assertThat(service.addMember(group.getConversationId(),
                new AddChatMemberRequest(bob.getUserId()), authentication).userId()).isEqualTo(bob.getUserId());
        verify(members).saveAndFlush(any(ChatConversationMemberEntity.class));
    }

    @Test
    void exactRetrySurvivesEditAndComparesReplyFileOrderAndMetadata() {
        ChatMessageEntity reply = ChatMessageEntity.create(conversation, 1L, bob, "reply", ChatMessageType.TEXT,
                "previous", null);
        ReflectionTestUtils.setField(reply, "id", 300L);
        ChatSendMessageRequest original = new ChatSendMessageRequest("same-id", ChatMessageType.MIXED,
                "hello", 300L, List.of(55L, 56L), "{\"a\":1}");
        ChatMessageEntity existing = ChatMessageEntity.create(conversation, 2L, alice, "same-id",
                ChatMessageType.MIXED, "hello", reply, null, original.metadataJson());
        ReflectionTestUtils.setField(existing, "id", 101L);
        existing.recordOriginalPayload(ReflectionTestUtils.invokeMethod(service, "payloadFingerprint",
                original, original.metadataJson()));
        StoredFileEntity first = StoredFileEntity.builder().id(55L).accessScope("CHAT").build();
        StoredFileEntity second = StoredFileEntity.builder().id(56L).accessScope("CHAT").build();
        when(messageFiles.findByMessageIdOrderByPositionAscIdAsc(101L)).thenReturn(List.of(
                ChatMessageFileEntity.create(existing, first, 0), ChatMessageFileEntity.create(existing, second, 1)));
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        when(messages.findBySender_IdAndClientMessageId(alice.getId(), "same-id"))
                .thenReturn(Optional.of(existing));

        existing.edit("edited since send");
        assertThat(service.sendMessage(conversation.getConversationId(), original, authentication).id())
                .isEqualTo(101L);
        List<ChatSendMessageRequest> changed = List.of(
                new ChatSendMessageRequest("same-id", ChatMessageType.MIXED, "different", 300L,
                        List.of(55L, 56L), original.metadataJson()),
                new ChatSendMessageRequest("same-id", ChatMessageType.MIXED, "hello", 301L,
                        List.of(55L, 56L), original.metadataJson()),
                new ChatSendMessageRequest("same-id", ChatMessageType.MIXED, "hello", 300L,
                        List.of(56L, 55L), original.metadataJson()),
                new ChatSendMessageRequest("same-id", ChatMessageType.MIXED, "hello", 300L,
                        List.of(55L, 56L), "{\"a\":2}"),
                new ChatSendMessageRequest("same-id", ChatMessageType.TEXT, "hello", 300L,
                        null, original.metadataJson()));
        for (ChatSendMessageRequest request : changed) {
            assertThatThrownBy(() -> service.sendMessage(conversation.getConversationId(), request, authentication))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
        existing.softDelete();
        assertThat(service.sendMessage(conversation.getConversationId(), original, authentication).id())
                .isEqualTo(101L);
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void gifSourceMustBeAWellFormedHttpsUrlWithoutCredentials() {
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembershipForUpdate(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        for (String source : List.of("http://example.test/a.gif", "//example.test/a.gif",
                "javascript:alert(1)", "https://user@example.test/a.gif", "https://example.test\\@evil.test/a.gif",
                "https://example.test/a gif", "https://example.test/a.gif\n", "https:///a.gif")) {
            assertThatThrownBy(() -> service.sendMessage(conversation.getConversationId(),
                    new ChatSendMessageRequest("gif", ChatMessageType.GIF, source, null, null), authentication))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(messages, never()).saveAndFlush(any());
        service.sendMessage(conversation.getConversationId(),
                new ChatSendMessageRequest("valid-gif", ChatMessageType.GIF,
                        "https://example.test/a.gif?size=2", null, null), authentication);
        verify(messages).saveAndFlush(any(ChatMessageEntity.class));
    }

    private ChatConversationEntity groupConversation() {
        ChatConversationEntity group = ChatConversationEntity.createGroup("Research group", alice, null);
        ReflectionTestUtils.setField(group, "id", 20L);
        ReflectionTestUtils.setField(group, "conversationId", "group-1");
        return group;
    }

    private void authenticateAs(UserEntity user) {
        when(authentication.getName()).thenReturn(user.getEmail());
        when(projectAccess.requireAuthenticatedUser(user.getEmail())).thenReturn(user);
    }

    private ChatMessageEntity message(long sequence, String content) {
        ChatMessageEntity message = ChatMessageEntity.create(
                conversation, sequence, alice, "client-" + sequence, ChatMessageType.TEXT, content, null);
        ReflectionTestUtils.setField(message, "id", sequence + 100L);
        return message;
    }

    private UserEntity user(long id, String userId, String email) {
        return UserEntity.builder()
                .id(id).userId(userId).name(userId).email(email)
                .isActive(true).isAccountVerified(true).build();
    }
}
