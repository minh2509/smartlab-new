package com.smartlab.service.impl;

import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.response.ChatMessagePageResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.entity.ChatConversationEntity;
import com.smartlab.entity.ChatConversationMemberEntity;
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

import java.util.List;
import java.util.Optional;
import java.util.Collection;

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
        when(projectAccess.requireAuthenticatedUser(alice.getEmail())).thenReturn(alice);
        lenient().when(permissions.hasInactiveAssignedRole(any(UserEntity.class))).thenReturn(false);
        lenient().when(messages.findLatest(anyLong(), any())).thenReturn(List.of());
        lenient().when(messageFiles.findByMessageIdOrderByPositionAscIdAsc(any())).thenReturn(List.of());
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
        when(members.findMembership(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));
        when(messages.findBySender_IdAndClientMessageId(alice.getId(), "client-1"))
                .thenReturn(Optional.of(existing));

        ChatMessageResponse response = service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("client-1", ChatMessageType.TEXT, "retry", null, null),
                authentication
        );

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.content()).isEqualTo("hello");
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void groupMemberCanSendAndSequenceAdvancesBeforePersistence() {
        ChatConversationMemberEntity bobMember = ChatConversationMemberEntity.create(
                conversation, bob, ChatMembershipRole.MEMBER);
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembership(conversation.getConversationId(), alice.getId()))
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
        verify(messages).saveAndFlush(any(ChatMessageEntity.class));

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
        when(members.findMembership(conversation.getConversationId(), alice.getId()))
                .thenReturn(Optional.of(aliceMember));

        assertThatThrownBy(() -> service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("client-3", ChatMessageType.TEXT, "hello", null, null),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
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
                .storageKey("report").ownerUser(bob).build();
        when(conversations.findByConversationIdForUpdate(conversation.getConversationId()))
                .thenReturn(Optional.of(conversation));
        when(members.findMembership(conversation.getConversationId(), alice.getId()))
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
        when(messages.saveAndFlush(any(ChatMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ChatMessageResponse response = service.sendMessage(
                conversation.getConversationId(),
                new ChatSendMessageRequest("file-2", ChatMessageType.FILE, null, null, List.of(55L)),
                authentication
        );
        assertThat(response.messageType()).isEqualTo(ChatMessageType.FILE);
        verify(messageFiles).save(any());
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
