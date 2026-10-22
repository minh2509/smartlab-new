package com.smartlab.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.dto.request.AddChatMemberRequest;
import com.smartlab.dto.request.ChangeChatMemberRoleRequest;
import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.ChatTypingRequest;
import com.smartlab.dto.request.CreateGroupChatRequest;
import com.smartlab.dto.request.EditChatMessageRequest;
import com.smartlab.dto.request.RenameChatRequest;
import com.smartlab.dto.request.ChatPreferenceRequest;
import com.smartlab.dto.request.ChatReactionRequest;
import com.smartlab.dto.request.ChatPollVoteRequest;
import com.smartlab.dto.request.ForwardChatMessageRequest;
import com.smartlab.dto.response.ChatConversationEventData;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMemberEventData;
import com.smartlab.dto.response.ChatMemberResponse;
import com.smartlab.dto.response.ChatMessageFileResponse;
import com.smartlab.dto.response.ChatMessagePageResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.dto.response.ChatMessageReferenceResponse;
import com.smartlab.dto.response.ChatReactionResponse;
import com.smartlab.dto.response.ChatReadResponse;
import com.smartlab.dto.response.ChatTypingEventData;
import com.smartlab.dto.response.ChatUserSummary;
import com.smartlab.entity.ChatConversationEntity;
import com.smartlab.entity.ChatConversationMemberEntity;
import com.smartlab.entity.ChatMessageEntity;
import com.smartlab.entity.ChatMessageFileEntity;
import com.smartlab.entity.ChatMessageReactionEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.enums.ChatConversationType;
import com.smartlab.enums.ChatMembershipRole;
import com.smartlab.enums.ChatMessageType;
import com.smartlab.repo.ChatConversationMemberRepository;
import com.smartlab.repo.ChatConversationRepository;
import com.smartlab.repo.ChatMessageFileRepository;
import com.smartlab.repo.ChatMessageRepository;
import com.smartlab.repo.ChatMessageReactionRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.ChatRealtimePublisher;
import com.smartlab.service.ChatService;
import com.smartlab.service.ChatPresenceService;
import com.smartlab.service.FileService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.ProjectAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_GROUP_MEMBERS = 100;

    private final ChatConversationRepository conversationRepository;
    private final ChatConversationMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageFileRepository messageFileRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;
    private final ChatRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatPresenceService presenceService;

    @Autowired
    public ChatServiceImpl(
            ChatConversationRepository conversationRepository,
            ChatConversationMemberRepository memberRepository,
            ChatMessageRepository messageRepository,
            ChatMessageFileRepository messageFileRepository,
            ChatMessageReactionRepository reactionRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            StoredFileRepository storedFileRepository,
            FileService fileService,
            ProjectAccessService projectAccessService,
            PermissionService permissionService,
            ChatRealtimePublisher realtimePublisher,
            ChatPresenceService presenceService
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.messageFileRepository = messageFileRepository;
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileService = fileService;
        this.projectAccessService = projectAccessService;
        this.permissionService = permissionService;
        this.realtimePublisher = realtimePublisher;
        this.presenceService = presenceService;
    }

    /** Backward-compatible constructor retained for focused unit tests and embedders. */
    public ChatServiceImpl(
            ChatConversationRepository conversationRepository,
            ChatConversationMemberRepository memberRepository,
            ChatMessageRepository messageRepository,
            ChatMessageFileRepository messageFileRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            StoredFileRepository storedFileRepository,
            FileService fileService,
            ProjectAccessService projectAccessService,
            PermissionService permissionService,
            ChatRealtimePublisher realtimePublisher
    ) {
        this(conversationRepository, memberRepository, messageRepository, messageFileRepository, null,
                userRepository, projectRepository, storedFileRepository, fileService, projectAccessService,
                permissionService, realtimePublisher, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listConversations(Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        List<ChatConversationMemberEntity> memberships = memberRepository.findActiveByUserId(currentUser.getId());
        if (memberships.isEmpty()) return List.of();
        List<Long> conversationIds = memberships.stream()
                .map(member -> member.getConversation().getId()).toList();
        Map<Long, List<ChatConversationMemberEntity>> membersByConversation =
                memberRepository.findActiveMembersByConversationIds(conversationIds).stream()
                        .collect(Collectors.groupingBy(member -> member.getConversation().getId()));
        List<ChatMessageEntity> latestMessages = messageRepository.findLatestByConversationIds(conversationIds);
        Map<String, ChatMessageResponse> latestByConversation = mapMessages(latestMessages).stream()
                .collect(Collectors.toMap(ChatMessageResponse::conversationId, Function.identity()));
        return memberships.stream().map(member -> {
            ChatConversationEntity conversation = member.getConversation();
            return toConversationResponse(conversation, currentUser.getId(),
                    membersByConversation.getOrDefault(conversation.getId(), List.of()),
                    latestByConversation.get(conversation.getConversationId()));
        }).toList();
    }

    @Override
    @Transactional
    public ChatConversationResponse createDirect(String targetUserId, Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        String normalizedTargetUserId = normalizeUserId(targetUserId, "Target user id is required");
        if (Objects.equals(currentUser.getUserId(), normalizedTargetUserId)) {
            throw badRequest("A direct chat requires two different users");
        }

        LinkedHashSet<String> publicUserIds = new LinkedHashSet<>();
        publicUserIds.add(currentUser.getUserId());
        publicUserIds.add(normalizedTargetUserId);
        List<UserEntity> lockedUsers = userRepository.findAllByUserIdInForUpdate(publicUserIds);
        Map<String, UserEntity> users = indexUsers(lockedUsers);
        UserEntity targetUser = users.get(normalizedTargetUserId);
        if (targetUser == null) {
            throw notFound("User not found");
        }
        requireActiveAccount(targetUser, "Target account is inactive");

        String directKey = ChatConversationEntity.buildDirectKey(currentUser.getId(), targetUser.getId());
        ChatConversationEntity conversation = conversationRepository.findByDirectKey(directKey).orElse(null);
        boolean created = conversation == null;
        if (created) {
            conversation = ChatConversationEntity.createDirect(currentUser, targetUser, currentUser);
            conversationRepository.saveAndFlush(conversation);
            memberRepository.saveAll(List.of(
                    ChatConversationMemberEntity.create(conversation, currentUser, ChatMembershipRole.MEMBER),
                    ChatConversationMemberEntity.create(conversation, targetUser, ChatMembershipRole.MEMBER)
            ));
            memberRepository.flush();
        } else {
            ensureDirectMembership(conversation, currentUser);
            ensureDirectMembership(conversation, targetUser);
        }

        ChatConversationResponse response = toConversationResponse(conversation, currentUser.getId());
        if (created) {
            publishConversation("chat.conversation.created", conversation);
        }
        return response;
    }

    @Override
    @Transactional
    public ChatConversationResponse createGroup(
            CreateGroupChatRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        if (request == null) {
            throw badRequest("Group request is required");
        }
        String title = normalizeTitle(request.title());
        if (request.memberUserIds() == null || request.memberUserIds().size() > MAX_GROUP_MEMBERS) {
            throw badRequest("A group cannot have more than 100 requested members");
        }
        ProjectEntity project = request.projectId() == null
                ? null
                : projectRepository.findByIdAndDeletedAtIsNull(request.projectId())
                .orElseThrow(() -> notFound("Project not found"));
        if (project != null) {
            projectAccessService.requireRead(project, currentUser.getEmail());
        }

        LinkedHashSet<String> requestedIds = new LinkedHashSet<>();
        requestedIds.add(currentUser.getUserId());
        for (String requestedId : request.memberUserIds()) {
            requestedIds.add(normalizeUserId(requestedId, "Member user id is required"));
        }
        if (requestedIds.size() > MAX_GROUP_MEMBERS) {
            throw badRequest("A group cannot have more than 100 active members");
        }
        List<UserEntity> lockedUsers = userRepository.findAllByUserIdInForUpdate(requestedIds);
        Map<String, UserEntity> users = indexUsers(lockedUsers);
        List<UserEntity> members = new ArrayList<>();
        for (String publicUserId : requestedIds) {
            UserEntity user = users.get(publicUserId);
            if (user == null) {
                throw notFound("Member account not found");
            }
            requireActiveAccount(user, "Member account is inactive");
            members.add(user);
        }

        ChatConversationEntity conversation = ChatConversationEntity.createGroup(title, currentUser, project);
        conversationRepository.saveAndFlush(conversation);
        List<ChatConversationMemberEntity> memberships = new ArrayList<>();
        memberships.add(ChatConversationMemberEntity.create(conversation, currentUser, ChatMembershipRole.OWNER));
        for (UserEntity user : members) {
            if (!Objects.equals(user.getId(), currentUser.getId())) {
                memberships.add(ChatConversationMemberEntity.create(conversation, user, ChatMembershipRole.MEMBER));
            }
        }
        memberRepository.saveAll(memberships);
        memberRepository.flush();

        publishConversation("chat.conversation.created", conversation);
        return toConversationResponse(conversation, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatMessagePageResponse listMessages(
            String conversationId,
            Long beforeSeq,
            Long afterSeq,
            int limit,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        if (beforeSeq != null && afterSeq != null) {
            throw badRequest("Use either beforeSeq or afterSeq, not both");
        }
        int pageSize = validatePageSize(limit);
        ChatConversationEntity conversation = requireConversation(conversationId);
        requireActiveMembership(conversation.getConversationId(), currentUser.getId());

        if (afterSeq != null) {
            if (afterSeq < 0) throw badRequest("afterSeq must not be negative");
            List<ChatMessageEntity> messages = messageRepository.findCatchUpAfter(
                    conversation.getId(), afterSeq, PageRequest.of(0, pageSize));
            boolean hasMore = !messages.isEmpty()
                    && messageRepository.existsByConversation_IdAndMessageSeqGreaterThan(
                    conversation.getId(), messages.get(messages.size() - 1).getMessageSeq());
            return new ChatMessagePageResponse(
                    mapMessages(messages),
                    null,
                    hasMore ? messages.get(messages.size() - 1).getMessageSeq() : null,
                    hasMore
            );
        }

        long cursor = beforeSeq == null ? latestSequence(conversation) + 1 : beforeSeq;
        if (cursor <= 0) throw badRequest("beforeSeq must be positive");
        List<ChatMessageEntity> messages = new ArrayList<>(messageRepository.findHistoryBefore(
                conversation.getId(), cursor, PageRequest.of(0, pageSize)));
        boolean hasMore = !messages.isEmpty()
                && messageRepository.existsByConversation_IdAndMessageSeqLessThan(
                conversation.getId(), messages.get(messages.size() - 1).getMessageSeq());
        Collections.reverse(messages);
        return new ChatMessagePageResponse(
                mapMessages(messages),
                hasMore ? messages.get(0).getMessageSeq() : null,
                null,
                hasMore
        );
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(
            String conversationId,
            ChatSendMessageRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        if (request == null) throw badRequest("Message request is required");
        ChatConversationEntity conversation = conversationRepository.findByConversationIdForUpdate(conversationId)
                .orElseThrow(() -> notFound("Conversation not found"));
        ChatConversationMemberEntity senderMembership = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireOpen(conversation);

        String clientMessageId = normalizeClientMessageId(request.clientMessageId());
        validateMessagePayload(request.messageType(), request.content(), request.fileIds());
        String metadata = normalizeMetadata(request.messageType(), request.metadataJson());
        String fingerprint = payloadFingerprint(request, metadata);
        ChatMessageEntity existing = messageRepository
                .findBySender_IdAndClientMessageId(currentUser.getId(), clientMessageId)
                .orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getConversation().getId(), conversation.getId())) {
                throw conflict("Client message id was already used in another conversation");
            }
            if (existing.getForwardedFrom() != null || !(existing.getOriginalPayloadFingerprint() == null
                    ? matchesLegacyPayload(existing, request, metadata)
                    : existing.getOriginalPayloadFingerprint().equals(fingerprint))) {
                throw conflict("Client message id was already used for another message");
            }
            return toMessageResponse(existing, messageFiles(existing));
        }
        ChatMessageEntity replyTo = resolveReply(conversation, request.replyToMessageId());
        List<StoredFileEntity> files = resolveAttachments(request.fileIds(), currentUser, authentication);
        long messageSeq = conversation.nextMessageSequence();
        ChatMessageEntity message = ChatMessageEntity.create(
                conversation,
                messageSeq,
                currentUser,
                clientMessageId,
                request.messageType(),
                normalizeContent(request.content()),
                replyTo,
                null,
                metadata
        );
        message.recordOriginalPayload(fingerprint);
        messageRepository.saveAndFlush(message);
        senderMembership.markRead(messageSeq);
        memberRepository.saveAndFlush(senderMembership);
        for (int index = 0; index < files.size(); index++) {
            messageFileRepository.save(ChatMessageFileEntity.create(message, files.get(index), index));
        }
        if (!files.isEmpty()) messageFileRepository.flush();

        ChatMessageResponse response = toMessageResponse(message, messageFiles(message));
        publishMessage("chat.message.created", conversation, response);
        return response;
    }

    @Override
    @Transactional
    public ChatReadResponse markRead(
            String conversationId,
            ChatReadRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        if (request == null) throw badRequest("Read request is required");
        ChatConversationEntity conversation = conversationRepository.findByConversationIdForUpdate(conversationId)
                .orElseThrow(() -> notFound("Conversation not found"));
        ChatConversationMemberEntity member = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        long requested = request.lastReadSeq();
        if (requested < 0) {
            throw badRequest("lastReadSeq must not be negative");
        }
        if (requested > latestSequence(conversation)) {
            throw badRequest("lastReadSeq cannot exceed the latest message sequence");
        }
        long current = safeSequence(member.getLastReadSeq());
        if (requested > current) {
            member.markRead(requested);
            memberRepository.saveAndFlush(member);
            ChatReadResponse data = new ChatReadResponse(
                    conversation.getConversationId(), currentUser.getUserId(), requested);
            publish("chat.read.updated", conversation.getConversationId(), data, recipientEmails(conversation));
            return data;
        }
        return new ChatReadResponse(conversation.getConversationId(), currentUser.getUserId(), current);
    }

    @Override
    @Transactional
    public ChatConversationResponse renameGroup(
            String conversationId,
            RenameChatRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        ChatConversationMemberEntity actor = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireGroup(conversation);
        requireOpen(conversation);
        requireManager(actor);
        conversation.rename(normalizeTitle(request == null ? null : request.title()));
        conversationRepository.saveAndFlush(conversation);
        publishConversation("chat.conversation.updated", conversation);
        return toConversationResponse(conversation, currentUser.getId());
    }

    @Override
    @Transactional
    public ChatMemberResponse addMember(
            String conversationId,
            AddChatMemberRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        ChatConversationMemberEntity actor = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireGroup(conversation);
        requireOpen(conversation);
        requireManager(actor);
        String userId = normalizeUserId(request == null ? null : request.userId(), "User id is required");
        UserEntity target = userRepository.findByUserId(userId)
                .orElseThrow(() -> notFound("Member account not found"));
        requireActiveAccount(target, "Member account is inactive");
        ChatConversationMemberEntity member = memberRepository.findMembership(
                conversation.getConversationId(), target.getId()).orElse(null);
        if (member != null && member.isActive()) {
            throw conflict("User is already an active conversation member");
        }
        if (memberRepository.findActiveMembers(conversationId).size() >= MAX_GROUP_MEMBERS) {
            throw conflict("A group cannot have more than 100 active members");
        }
        if (member == null) {
            member = ChatConversationMemberEntity.create(conversation, target, ChatMembershipRole.MEMBER);
        } else {
            member.rejoin(ChatMembershipRole.MEMBER);
        }
        memberRepository.saveAndFlush(member);
        ChatMemberResponse response = toMemberResponse(member);
        publishMember("chat.member.added", conversation, response, List.of());
        return response;
    }

    @Override
    @Transactional
    public void removeMember(String conversationId, String userId, Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        ChatConversationMemberEntity actor = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireGroup(conversation);
        requireOpen(conversation);
        UserEntity target = userRepository.findByUserId(normalizeUserId(userId, "User id is required"))
                .orElseThrow(() -> notFound("Conversation member not found"));
        ChatConversationMemberEntity member = memberRepository.findMembershipForUpdate(
                        conversation.getConversationId(), target.getId())
                .orElseThrow(() -> notFound("Conversation member not found"));
        boolean removingSelf = Objects.equals(currentUser.getId(), target.getId());
        if (member.getMembershipRole() == ChatMembershipRole.OWNER) {
            throw conflict(removingSelf
                    ? "The conversation owner cannot leave the group"
                    : "The conversation owner cannot be removed");
        }
        if (!member.isActive()) return;
        if (!removingSelf) {
            requireManager(actor);
            if (actor.getMembershipRole() == ChatMembershipRole.ADMIN
                    && member.getMembershipRole() != ChatMembershipRole.MEMBER) {
                throw forbidden("Conversation admins can only remove members");
            }
        }
        member.leave();
        memberRepository.saveAndFlush(member);
        publishMember("chat.member.removed", conversation, toMemberResponse(member), List.of(target.getEmail()));
    }

    @Override
    @Transactional
    public void leaveGroupWithSuccessor(String conversationId, String successorUserId,
                                        Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        requireGroup(conversation);
        requireOpen(conversation);
        ChatConversationMemberEntity owner = requireActiveMembershipForUpdate(conversationId, currentUser.getId());
        requireOwner(owner);
        String successorId = normalizeUserId(successorUserId, "Successor user id is required");
        if (successorId.equals(currentUser.getUserId())) throw badRequest("Choose another active member");
        UserEntity successorUser = userRepository.findByUserId(successorId)
                .orElseThrow(() -> notFound("Successor not found"));
        ChatConversationMemberEntity successor = requireActiveMembershipForUpdate(
                conversationId, successorUser.getId());
        requireActiveAccount(successorUser, "Successor account is inactive");
        // DB partial unique index prevents two active owners even during a concurrent write.
        owner.changeRole(ChatMembershipRole.MEMBER);
        owner.leave();
        memberRepository.saveAndFlush(owner);
        successor.changeRole(ChatMembershipRole.OWNER);
        memberRepository.saveAndFlush(successor);
        publishMember("chat.member.role_changed", conversation, toMemberResponse(successor), List.of());
        publishMember("chat.member.removed", conversation, toMemberResponse(owner), List.of(currentUser.getEmail()));
    }

    @Override
    @Transactional
    public ChatConversationResponse closeGroup(String conversationId, Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        requireGroup(conversation);
        requireOwner(requireActiveMembershipForUpdate(conversationId, currentUser.getId()));
        if (conversation.getClosedAt() == null) {
            conversation.close();
            conversationRepository.saveAndFlush(conversation);
            publishConversation("chat.conversation.updated", conversation);
        }
        return toConversationResponse(conversation, currentUser.getId());
    }

    @Override
    @Transactional
    public ChatMemberResponse changeMemberRole(
            String conversationId,
            String userId,
            ChangeChatMemberRoleRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        ChatConversationMemberEntity actor = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireGroup(conversation);
        requireOpen(conversation);
        requireOwner(actor);
        if (request == null || request.role() == null || request.role() == ChatMembershipRole.OWNER) {
            throw badRequest("Only MEMBER or ADMIN can be assigned to a member");
        }
        UserEntity target = userRepository.findByUserId(normalizeUserId(userId, "User id is required"))
                .orElseThrow(() -> notFound("Conversation member not found"));
        ChatConversationMemberEntity member = memberRepository.findMembershipForUpdate(
                        conversation.getConversationId(), target.getId())
                .filter(ChatConversationMemberEntity::isActive)
                .orElseThrow(() -> notFound("Conversation member not found"));
        if (member.getMembershipRole() == ChatMembershipRole.OWNER) {
            throw conflict("The conversation owner role cannot be changed");
        }
        if (member.getMembershipRole() != request.role()) {
            member.rejoin(request.role());
            memberRepository.saveAndFlush(member);
            publishMember("chat.member.role_changed", conversation, toMemberResponse(member), List.of());
        }
        return toMemberResponse(member);
    }

    @Override
    @Transactional
    public ChatMessageResponse editMessage(
            String conversationId,
            Long messageId,
            EditChatMessageRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireOpen(conversation);
        ChatMessageEntity message = requireMessage(conversation, messageId);
        if (message.getDeletedAt() != null) throw conflict("Deleted messages cannot be edited");
        if (message.getMessageType() != ChatMessageType.TEXT
                && message.getMessageType() != ChatMessageType.MIXED) {
            throw conflict("Only text messages can be edited");
        }
        if (!isMessageSender(message, currentUser)) {
            throw forbidden("You can only edit your own messages");
        }
        String content = normalizeContent(request == null ? null : request.content());
        if (content == null) throw badRequest("Message content is required");
        message.edit(content);
        messageRepository.saveAndFlush(message);
        ChatMessageResponse response = toMessageResponse(message, messageFiles(message));
        publishMessage("chat.message.updated", conversation, response);
        return response;
    }

    @Override
    @Transactional
    public void deleteMessage(String conversationId, Long messageId, Authentication authentication) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        ChatConversationMemberEntity actor = requireActiveMembershipForUpdate(
                conversation.getConversationId(), currentUser.getId());
        requireOpen(conversation);
        ChatMessageEntity message = requireMessage(conversation, messageId);
        if (!canDeleteMessage(message, actor, currentUser)) {
            throw forbidden("You do not have permission to delete this message");
        }
        if (message.getDeletedAt() != null) return;
        message.softDelete();
        messageRepository.saveAndFlush(message);
        publishMessage("chat.message.deleted", conversation,
                toMessageResponse(message, messageFiles(message)));
    }

    @Override
    @Transactional
    public ChatConversationResponse updatePreferences(
            String conversationId,
            ChatPreferenceRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = requireConversation(conversationId);
        ChatConversationMemberEntity member = requireActiveMembershipForUpdate(conversationId, currentUser.getId());
        if (request == null) throw badRequest("Preference request is required");
        if (request.pinned() != null) member.setPinned(request.pinned());
        if (request.muted() != null) member.setMuted(request.muted());
        if (request.archived() != null) {
            if (request.archived()) member.archive(); else member.unarchive();
        }
        memberRepository.saveAndFlush(member);
        return toConversationResponse(conversation, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(
            String conversationId,
            String query,
            int limit,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = requireConversation(conversationId);
        requireActiveMembership(conversationId, currentUser.getId());
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) throw badRequest("Search query is required");
        return mapMessages(messageRepository.searchContent(
                conversation.getId(), normalized, PageRequest.of(0, validatePageSize(limit))));
    }

    @Override
    @Transactional
    public ChatMessageResponse toggleReaction(
            String conversationId,
            Long messageId,
            ChatReactionRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        requireActiveMembership(conversationId, currentUser.getId());
        requireOpen(conversation);
        ChatMessageEntity message = requireMessage(conversation, messageId);
        if (message.getDeletedAt() != null) throw conflict("Deleted messages cannot be reacted to");
        String emoji = request == null || request.emoji() == null ? "" : request.emoji().trim();
        if (emoji.isEmpty() || emoji.length() > 16) throw badRequest("Invalid reaction");
        reactionRepository.findOne(message.getId(), currentUser.getId(), emoji)
                .ifPresentOrElse(reactionRepository::delete,
                        () -> reactionRepository.save(ChatMessageReactionEntity.create(message, currentUser, emoji)));
        reactionRepository.flush();
        ChatMessageResponse response = toMessageResponse(message, messageFiles(message));
        publishMessage("chat.message.reactions_updated", conversation, response);
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponse forwardMessage(
            String sourceConversationId,
            Long messageId,
            String targetConversationId,
            ForwardChatMessageRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        if (targetConversationId == null || targetConversationId.isBlank()) {
            throw notFound("Target conversation not found");
        }
        // Lock both conversations in a stable order: closure of either one must
        // serialize with forwarding, without deadlocking cross-forwards.
        boolean sourceFirst = sourceConversationId.compareTo(targetConversationId) <= 0;
        ChatConversationEntity first = lockedConversation(sourceFirst ? sourceConversationId : targetConversationId);
        ChatConversationEntity second = sourceConversationId.equals(targetConversationId)
                ? first : lockedConversation(sourceFirst ? targetConversationId : sourceConversationId);
        ChatConversationEntity source = sourceFirst ? first : second;
        ChatConversationEntity target = sourceFirst ? second : first;
        // Membership can be revoked while acquiring either conversation lock.
        // Recheck it inside the locked section before copying source content/files.
        requireActiveMembership(sourceConversationId, currentUser.getId());
        requireOpen(source);
        ChatConversationMemberEntity senderMembership = requireActiveMembershipForUpdate(
                targetConversationId, currentUser.getId());
        requireOpen(target);
        ChatMessageEntity original = requireMessage(source, messageId);
        if (original.getDeletedAt() != null || original.getMessageType() == ChatMessageType.SYSTEM) {
            throw conflict("This message cannot be forwarded");
        }
        String clientMessageId = normalizeClientMessageId(request == null ? null : request.clientMessageId());
        ChatMessageEntity existing = messageRepository
                .findBySender_IdAndClientMessageId(currentUser.getId(), clientMessageId)
                .orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getConversation().getId(), target.getId())
                    || existing.getForwardedFrom() == null
                    || !Objects.equals(existing.getForwardedFrom().getId(), original.getId())) {
                throw conflict("Client message id was already used for another message");
            }
            return toMessageResponse(existing, messageFiles(existing));
        }
        long messageSeq = target.nextMessageSequence();
        ChatMessageEntity forwarded = ChatMessageEntity.create(
                target, messageSeq, currentUser, clientMessageId,
                original.getMessageType(), original.getContent(), null, original,
                normalizeMetadata(original.getMessageType(), original.getMetadataJson()));
        messageRepository.saveAndFlush(forwarded);
        senderMembership.markRead(messageSeq);
        memberRepository.saveAndFlush(senderMembership);
        List<ChatMessageFileEntity> originalFiles = messageFiles(original);
        for (int i = 0; i < originalFiles.size(); i++) {
            messageFileRepository.save(ChatMessageFileEntity.create(forwarded, originalFiles.get(i).getFile(), i));
        }
        messageFileRepository.flush();
        ChatMessageResponse response = toMessageResponse(forwarded, messageFiles(forwarded));
        publishMessage("chat.message.created", target, response);
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponse votePoll(
            String conversationId,
            Long messageId,
            ChatPollVoteRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = lockedConversation(conversationId);
        requireActiveMembership(conversationId, currentUser.getId());
        requireOpen(conversation);
        ChatMessageEntity message = messageRepository.findByIdForUpdate(messageId)
                .orElseThrow(() -> notFound("Poll not found"));
        if (!Objects.equals(message.getConversation().getId(), conversation.getId())
                || message.getMessageType() != ChatMessageType.POLL || message.getDeletedAt() != null) {
            throw notFound("Poll not found");
        }
        String optionId = request == null ? null : request.optionId();
        try {
            ObjectNode metadata = (ObjectNode) objectMapper.readTree(message.getMetadataJson());
            ArrayNode options = (ArrayNode) metadata.path("options");
            boolean multiple = metadata.path("multiple").asBoolean(false);
            boolean found = false;
            for (JsonNode node : options) {
                if (!(node instanceof ObjectNode option)) continue;
                ArrayNode voters = option.withArray("voterIds");
                boolean selected = containsText(voters, currentUser.getUserId());
                if (Objects.equals(option.path("id").asText(), optionId)) {
                    found = true;
                    if (selected) removeText(voters, currentUser.getUserId());
                    else voters.add(currentUser.getUserId());
                } else if (!multiple) {
                    removeText(voters, currentUser.getUserId());
                }
            }
            if (!found) throw badRequest("Poll option not found");
            message.updateMetadata(objectMapper.writeValueAsString(metadata));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("Invalid poll data");
        }
        messageRepository.saveAndFlush(message);
        ChatMessageResponse response = toMessageResponse(message, messageFiles(message));
        publishMessage("chat.message.updated", conversation, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public void publishTyping(
            String conversationId,
            ChatTypingRequest request,
            Authentication authentication
    ) {
        UserEntity currentUser = currentUser(authentication);
        ChatConversationEntity conversation = requireConversation(conversationId);
        requireActiveMembership(conversation.getConversationId(), currentUser.getId());
        requireOpen(conversation);
        if (request == null) throw badRequest("Typing request is required");
        ChatTypingEventData data = new ChatTypingEventData(
                conversation.getConversationId(), userSummary(currentUser));
        publish(
                request.started() ? "chat.typing.started" : "chat.typing.stopped",
                conversation.getConversationId(),
                data,
                recipientEmails(conversation)
        );
    }

    private UserEntity currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return projectAccessService.requireAuthenticatedUser(authentication.getName());
    }

    private ChatConversationEntity requireConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) throw notFound("Conversation not found");
        return conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> notFound("Conversation not found"));
    }

    private ChatConversationEntity lockedConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) throw notFound("Conversation not found");
        return conversationRepository.findByConversationIdForUpdate(conversationId)
                .orElseThrow(() -> notFound("Conversation not found"));
    }

    private ChatConversationMemberEntity requireActiveMembership(String conversationId, Long userId) {
        return memberRepository.findMembership(conversationId, userId)
                .filter(ChatConversationMemberEntity::isActive)
                .orElseThrow(() -> notFound("Conversation not found"));
    }

    private ChatConversationMemberEntity requireActiveMembershipForUpdate(String conversationId, Long userId) {
        return memberRepository.findMembershipForUpdate(conversationId, userId)
                .filter(ChatConversationMemberEntity::isActive)
                .orElseThrow(() -> notFound("Conversation not found"));
    }

    private void ensureDirectMembership(ChatConversationEntity conversation, UserEntity user) {
        ChatConversationMemberEntity member = memberRepository.findMembership(
                conversation.getConversationId(), user.getId()).orElse(null);
        if (member == null) {
            memberRepository.save(ChatConversationMemberEntity.create(
                    conversation, user, ChatMembershipRole.MEMBER));
        } else if (!member.isActive()) {
            member.rejoin(ChatMembershipRole.MEMBER);
            memberRepository.save(member);
        }
        memberRepository.flush();
    }

    private void requireGroup(ChatConversationEntity conversation) {
        if (conversation.getConversationType() != ChatConversationType.GROUP) {
            throw conflict("This operation is only available for group conversations");
        }
    }

    private void requireOpen(ChatConversationEntity conversation) {
        if (conversation.getClosedAt() != null) throw conflict("This group is closed and read-only");
    }

    private void requireManager(ChatConversationMemberEntity member) {
        if (member.getMembershipRole() != ChatMembershipRole.OWNER
                && member.getMembershipRole() != ChatMembershipRole.ADMIN) {
            throw forbidden("Conversation management permission is required");
        }
    }

    private void requireOwner(ChatConversationMemberEntity member) {
        if (member.getMembershipRole() != ChatMembershipRole.OWNER) {
            throw forbidden("Conversation owner permission is required");
        }
    }

    private ChatMessageEntity requireMessage(ChatConversationEntity conversation, Long messageId) {
        if (messageId == null || messageId <= 0) throw notFound("Message not found");
        ChatMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> notFound("Message not found"));
        if (!Objects.equals(message.getConversation().getId(), conversation.getId())) {
            throw notFound("Message not found");
        }
        return message;
    }

    private boolean isMessageSender(
            ChatMessageEntity message,
            UserEntity currentUser
    ) {
        return message.getSender() != null && Objects.equals(message.getSender().getId(), currentUser.getId());
    }

    private boolean canDeleteMessage(
            ChatMessageEntity message,
            ChatConversationMemberEntity actor,
            UserEntity currentUser
    ) {
        return isMessageSender(message, currentUser)
                || actor.getMembershipRole() == ChatMembershipRole.OWNER
                || actor.getMembershipRole() == ChatMembershipRole.ADMIN;
    }

    private ChatMessageEntity resolveReply(ChatConversationEntity conversation, Long replyToMessageId) {
        if (replyToMessageId == null) return null;
        ChatMessageEntity reply = messageRepository.findById(replyToMessageId)
                .orElseThrow(() -> notFound("Reply message not found"));
        if (!Objects.equals(reply.getConversation().getId(), conversation.getId())) {
            throw notFound("Reply message not found");
        }
        return reply;
    }

    private List<StoredFileEntity> resolveAttachments(
            List<Long> fileIds,
            UserEntity currentUser,
            Authentication authentication
    ) {
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        if (new LinkedHashSet<>(fileIds).size() != fileIds.size()) {
            throw badRequest("Attachment file ids must be unique");
        }
        List<StoredFileEntity> files = new ArrayList<>();
        for (Long fileId : fileIds) {
            StoredFileEntity file = storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                    .orElseThrow(() -> notFound("Attachment file not found"));
            if (!fileService.canRead(fileId, authentication)) {
                throw forbidden("You do not have access to attachment file " + fileId);
            }
            if (file.getOwnerUser() == null
                    || !Objects.equals(file.getOwnerUser().getId(), currentUser.getId())) {
                throw forbidden("Chat attachments must be uploaded by the message sender");
            }
            if (!"PRIVATE".equals(file.getAccessScope())) {
                throw conflict("Chat attachments must have PRIVATE scope before sending");
            }
            if (storedFileRepository.hasNonChatReference(fileId)) {
                throw conflict("A file already referenced outside chat cannot be attached to a message");
            }
            file.setAccessScope("CHAT");
            files.add(file);
        }
        if (!files.isEmpty()) storedFileRepository.saveAll(files);
        return files;
    }

    private void validateMessagePayload(ChatMessageType type, String content, List<Long> fileIds) {
        if (type == null || type == ChatMessageType.SYSTEM) {
            throw badRequest("Unsupported client message type");
        }
        boolean hasContent = content != null && !content.isBlank();
        boolean hasFiles = fileIds != null && !fileIds.isEmpty();
        boolean valid = switch (type) {
            case TEXT -> hasContent && !hasFiles;
            case FILE -> hasFiles && !hasContent;
            case MIXED -> hasContent && hasFiles;
            case IMAGE, VOICE -> hasFiles;
            case GIF, STICKER -> hasContent && !hasFiles;
            case POLL -> !hasFiles;
            case SYSTEM -> false;
        };
        if (!valid) throw badRequest("Message content and attachments do not match the message type");
        if (type == ChatMessageType.GIF) validateGifUrl(content);
    }

    private void validateGifUrl(String value) {
        try {
            if (value == null || !value.equals(value.trim()) || value.indexOf('\\') >= 0) {
                throw badRequest("GIF source must be a valid HTTPS URL");
            }
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
                throw badRequest("GIF source must be a valid HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw badRequest("GIF source must be a valid HTTPS URL");
        }
    }

    private String payloadFingerprint(ChatSendMessageRequest request, String metadata) {
        ArrayNode payload = objectMapper.createArrayNode();
        payload.add(request.messageType().name());
        payload.add(normalizeContent(request.content()));
        if (request.replyToMessageId() == null) payload.addNull();
        else payload.add(request.replyToMessageId());
        ArrayNode fileIds = payload.addArray();
        if (request.fileIds() != null) request.fileIds().forEach(fileId -> fileIds.add(fileId));
        payload.add(metadata);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // Pre-migration messages have no immutable original request; compare only unedited state.
    private boolean matchesLegacyPayload(ChatMessageEntity existing, ChatSendMessageRequest request, String metadata) {
        return existing.getDeletedAt() == null && existing.getEditedAt() == null
                && existing.getMessageType() == request.messageType()
                && Objects.equals(existing.getContent(), normalizeContent(request.content()))
                && Objects.equals(existing.getMetadataJson(), metadata)
                && Objects.equals(existing.getReplyTo() == null ? null : existing.getReplyTo().getId(),
                        request.replyToMessageId())
                && messageFiles(existing).stream().map(file -> file.getFile().getId()).toList()
                        .equals(request.fileIds() == null ? List.of() : request.fileIds());
    }

    private String normalizeMetadata(ChatMessageType type, String metadataJson) {
        if (metadataJson != null && metadataJson.length() > 20_000) {
            throw badRequest("Message metadata must not exceed 20000 characters");
        }
        if (type != ChatMessageType.POLL) {
            return metadataJson == null || metadataJson.isBlank() ? null : metadataJson;
        }
        if (metadataJson == null || metadataJson.isBlank()) throw badRequest("Poll data is required");
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            if (!root.isObject() || !root.path("question").isTextual()
                    || (root.has("multiple") && !root.path("multiple").isBoolean())) {
                throw badRequest("Invalid poll data");
            }
            String question = root.path("question").asText().trim();
            JsonNode options = root.path("options");
            if (question.isEmpty() || question.length() > 300 || !options.isArray()
                    || options.size() < 2 || options.size() > 10) {
                throw badRequest("A poll requires a question and 2 to 10 options");
            }
            ObjectNode cleanPoll = objectMapper.createObjectNode();
            cleanPoll.put("question", question);
            cleanPoll.put("multiple", root.path("multiple").asBoolean(false));
            ArrayNode cleanOptions = cleanPoll.putArray("options");
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonNode option : options) {
                if (!option.isObject() || !option.path("id").isTextual()
                        || !option.path("text").isTextual()) {
                    throw badRequest("Invalid poll option");
                }
                String id = option.path("id").asText().trim();
                String text = option.path("text").asText().trim();
                if (id.isEmpty() || id.length() > 80 || text.isEmpty() || text.length() > 200 || !ids.add(id)) {
                    throw badRequest("Invalid poll option");
                }
                // Votes are server-owned state: neither new polls nor forwards inherit client-supplied votes.
                ObjectNode cleanOption = cleanOptions.addObject();
                cleanOption.put("id", id);
                cleanOption.put("text", text);
                cleanOption.putArray("voterIds");
            }
            return cleanPoll.toString();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("Invalid poll data");
        }
    }

    private boolean containsText(ArrayNode array, String value) {
        for (JsonNode node : array) if (value.equals(node.asText())) return true;
        return false;
    }

    private void removeText(ArrayNode array, String value) {
        for (int i = array.size() - 1; i >= 0; i--) {
            if (value.equals(array.get(i).asText())) array.remove(i);
        }
    }

    private String normalizeClientMessageId(String value) {
        if (value == null || value.isBlank()) throw badRequest("Client message id is required");
        String normalized = value.trim();
        if (normalized.length() > 64) throw badRequest("Client message id must not exceed 64 characters");
        return normalized;
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) throw badRequest("Group title is required");
        String normalized = value.trim();
        if (normalized.length() > 200) throw badRequest("Group title must not exceed 200 characters");
        return normalized;
    }

    private String normalizeUserId(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        String normalized = value.trim();
        if (normalized.length() > 36) throw badRequest("User id must not exceed 36 characters");
        return normalized;
    }

    private String normalizeContent(String value) {
        if (value == null || value.isBlank()) return null;
        return value;
    }

    private int validatePageSize(int limit) {
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw badRequest("limit must be between 1 and 100");
        }
        return limit;
    }

    private long latestSequence(ChatConversationEntity conversation) {
        return safeSequence(conversation.getLastMessageSeq());
    }

    private long safeSequence(Long sequence) {
        return sequence == null ? 0L : sequence;
    }

    private Map<String, UserEntity> indexUsers(Collection<UserEntity> users) {
        return users.stream().collect(Collectors.toMap(
                UserEntity::getUserId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
    }

    private void requireActiveAccount(UserEntity user, String message) {
        if (!Boolean.TRUE.equals(user.getIsActive()) || permissionService.hasInactiveAssignedRole(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private ChatConversationResponse toConversationResponse(
            ChatConversationEntity conversation,
            Long currentUserId
    ) {
        List<ChatConversationMemberEntity> members = memberRepository.findActiveMembers(
                conversation.getConversationId());
        ChatMessageResponse lastMessage = messageRepository.findLatest(
                        conversation.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(message -> toMessageResponse(message, messageFiles(message)))
                .orElse(null);
        return toConversationResponse(conversation, currentUserId, members, lastMessage);
    }

    private ChatConversationResponse toConversationResponse(
            ChatConversationEntity conversation,
            Long currentUserId,
            List<ChatConversationMemberEntity> members,
            ChatMessageResponse lastMessage
    ) {
        ChatConversationMemberEntity currentMember = members.stream()
                .filter(member -> Objects.equals(member.getUser().getId(), currentUserId))
                .findFirst()
                .orElseThrow(() -> notFound("Conversation not found"));
        long lastMessageSeq = latestSequence(conversation);
        long lastReadSeq = safeSequence(currentMember.getLastReadSeq());
        String displayName = conversation.getConversationType() == ChatConversationType.GROUP
                ? conversation.getTitle()
                : members.stream()
                .filter(member -> !Objects.equals(member.getUser().getId(), currentUserId))
                .map(member -> member.getUser().getName())
                .findFirst()
                .orElse("Direct conversation");
        return new ChatConversationResponse(
                conversation.getConversationId(),
                conversation.getConversationType(),
                displayName,
                conversation.getTitle(),
                conversation.getProject() == null ? null : conversation.getProject().getId(),
                members.stream().map(this::toMemberResponse).toList(),
                lastMessage,
                lastMessageSeq,
                lastReadSeq,
                Math.max(0L, lastMessageSeq - lastReadSeq),
                Boolean.TRUE.equals(currentMember.getPinned()),
                Boolean.TRUE.equals(currentMember.getMuted()),
                currentMember.getArchivedAt() != null || conversation.getClosedAt() != null,
                conversation.getClosedAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private ChatConversationEventData toConversationEventData(ChatConversationEntity conversation) {
        return new ChatConversationEventData(
                conversation.getConversationId(),
                conversation.getConversationType(),
                conversation.getTitle(),
                conversation.getProject() == null ? null : conversation.getProject().getId(),
                memberRepository.findActiveMembers(conversation.getConversationId()).stream()
                        .map(this::toMemberResponse)
                        .toList(),
                latestSequence(conversation),
                conversation.getClosedAt(),
                conversation.getUpdatedAt()
        );
    }

    private ChatMemberResponse toMemberResponse(ChatConversationMemberEntity member) {
        UserEntity user = member.getUser();
        return new ChatMemberResponse(
                user.getUserId(),
                user.getName(),
                member.getMembershipRole(),
                member.getJoinedAt() == null ? null : member.getJoinedAt(),
                member.getLeftAt(),
                safeSequence(member.getLastReadSeq()),
                presenceService != null && presenceService.isOnline(user.getEmail()),
                presenceService == null ? null : presenceService.lastActiveAt(user.getEmail()),
                member.isActive()
        );
    }

    private ChatMessageResponse toMessageResponse(
            ChatMessageEntity message,
            List<ChatMessageFileEntity> files
    ) {
        List<ChatMessageReactionEntity> reactionEntities = reactionRepository == null
                ? List.of() : reactionRepository.findByMessageIds(List.of(message.getId()));
        return toMessageResponse(message, files, reactionEntities);
    }

    private List<ChatMessageResponse> mapMessages(List<ChatMessageEntity> messages) {
        if (messages.isEmpty()) return List.of();
        List<Long> ids = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<Long, List<ChatMessageFileEntity>> filesByMessage = messageFileRepository.findByMessageIds(ids).stream()
                .collect(Collectors.groupingBy(
                        file -> file.getMessage().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<ChatMessageReactionEntity>> reactionsByMessage = (reactionRepository == null
                ? List.<ChatMessageReactionEntity>of() : reactionRepository.findByMessageIds(ids)).stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getMessage().getId()));
        return messages.stream().map(message -> toMessageResponse(
                message,
                filesByMessage.getOrDefault(message.getId(), List.of()),
                reactionsByMessage.getOrDefault(message.getId(), List.of())
        )).toList();
    }

    private ChatMessageResponse toMessageResponse(
            ChatMessageEntity message,
            List<ChatMessageFileEntity> files,
            List<ChatMessageReactionEntity> reactionEntities
    ) {
        Map<String, List<ChatMessageReactionEntity>> grouped = reactionEntities.stream()
                .collect(Collectors.groupingBy(ChatMessageReactionEntity::getEmoji, LinkedHashMap::new, Collectors.toList()));
        return new ChatMessageResponse(
                message.getId(), message.getConversation().getConversationId(), safeSequence(message.getMessageSeq()),
                message.getSender() == null ? null : userSummary(message.getSender()), message.getClientMessageId(),
                message.getMessageType(), message.getDeletedAt() == null ? message.getContent() : null,
                message.getReplyTo() == null ? null : message.getReplyTo().getId(), toMessageReference(message.getReplyTo()),
                message.getForwardedFrom() == null ? null : message.getForwardedFrom().getId(),
                message.getDeletedAt() == null ? message.getMetadataJson() : null,
                grouped.entrySet().stream().map(entry -> new ChatReactionResponse(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().map(r -> r.getUser().getUserId()).toList())).toList(),
                message.getDeletedAt() == null
                        ? files.stream().map(file -> new ChatMessageFileResponse(file.getFile().getId(),
                                file.getFile().getOriginalName(), file.getFile().getMimeType(), file.getFile().getSizeBytes(),
                                file.getFile().getAccessScope())).toList()
                        : List.of(), message.getCreatedAt(), message.getEditedAt(),
                message.getDeletedAt());
    }

    private ChatMessageReferenceResponse toMessageReference(ChatMessageEntity message) {
        if (message == null) return null;
        return new ChatMessageReferenceResponse(message.getId(),
                message.getSender() == null ? null : userSummary(message.getSender()), message.getMessageType(),
                message.getDeletedAt() == null ? message.getContent() : null, message.getDeletedAt() != null);
    }

    private List<ChatMessageFileEntity> messageFiles(ChatMessageEntity message) {
        return messageFileRepository.findByMessageIdOrderByPositionAscIdAsc(message.getId());
    }

    private ChatUserSummary userSummary(UserEntity user) {
        return new ChatUserSummary(user.getUserId(), user.getName());
    }

    private List<String> recipientEmails(ChatConversationEntity conversation) {
        return memberRepository.findActiveMembers(conversation.getConversationId()).stream()
                .map(member -> member.getUser().getEmail())
                .filter(Objects::nonNull)
                .toList();
    }

    private void publishConversation(String event, ChatConversationEntity conversation) {
        publish(event, conversation.getConversationId(), toConversationEventData(conversation), recipientEmails(conversation));
    }

    private void publishMessage(String event, ChatConversationEntity conversation, ChatMessageResponse data) {
        publish(event, conversation.getConversationId(), data, recipientEmails(conversation));
    }

    private void publishMember(
            String event,
            ChatConversationEntity conversation,
            ChatMemberResponse member,
            Collection<String> extraRecipients
    ) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>(recipientEmails(conversation));
        recipients.addAll(extraRecipients);
        publish(event, conversation.getConversationId(),
                new ChatMemberEventData(conversation.getConversationId(), member), recipients);
    }

    private void publish(String event, String conversationId, Object data, Collection<String> recipients) {
        realtimePublisher.publishAfterCommit(event, conversationId, data, recipients);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
