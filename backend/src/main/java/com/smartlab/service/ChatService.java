package com.smartlab.service;

import com.smartlab.dto.request.AddChatMemberRequest;
import com.smartlab.dto.request.ChangeChatMemberRoleRequest;
import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.ChatTypingRequest;
import com.smartlab.dto.request.CreateGroupChatRequest;
import com.smartlab.dto.request.EditChatMessageRequest;
import com.smartlab.dto.request.RenameChatRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMemberResponse;
import com.smartlab.dto.response.ChatMessagePageResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.dto.response.ChatReadResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ChatService {
    List<ChatConversationResponse> listConversations(Authentication authentication);

    ChatConversationResponse createDirect(String targetUserId, Authentication authentication);

    ChatConversationResponse createGroup(CreateGroupChatRequest request, Authentication authentication);

    ChatMessagePageResponse listMessages(
            String conversationId,
            Long beforeSeq,
            Long afterSeq,
            int limit,
            Authentication authentication
    );

    ChatMessageResponse sendMessage(
            String conversationId,
            ChatSendMessageRequest request,
            Authentication authentication
    );

    ChatReadResponse markRead(
            String conversationId,
            ChatReadRequest request,
            Authentication authentication
    );

    ChatConversationResponse renameGroup(
            String conversationId,
            RenameChatRequest request,
            Authentication authentication
    );

    ChatMemberResponse addMember(
            String conversationId,
            AddChatMemberRequest request,
            Authentication authentication
    );

    void removeMember(String conversationId, String userId, Authentication authentication);

    ChatMemberResponse changeMemberRole(
            String conversationId,
            String userId,
            ChangeChatMemberRoleRequest request,
            Authentication authentication
    );

    ChatMessageResponse editMessage(
            String conversationId,
            Long messageId,
            EditChatMessageRequest request,
            Authentication authentication
    );

    void deleteMessage(String conversationId, Long messageId, Authentication authentication);

    void publishTyping(
            String conversationId,
            ChatTypingRequest request,
            Authentication authentication
    );
}
