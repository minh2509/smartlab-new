package com.smartlab.controller;

import com.smartlab.dto.request.AddChatMemberRequest;
import com.smartlab.dto.request.ChangeChatMemberRoleRequest;
import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.CreateDirectChatRequest;
import com.smartlab.dto.request.CreateGroupChatRequest;
import com.smartlab.dto.request.EditChatMessageRequest;
import com.smartlab.dto.request.RenameChatRequest;
import com.smartlab.dto.response.ChatConversationResponse;
import com.smartlab.dto.response.ChatMemberResponse;
import com.smartlab.dto.response.ChatMessagePageResponse;
import com.smartlab.dto.response.ChatMessageResponse;
import com.smartlab.dto.response.ChatReadResponse;
import com.smartlab.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/conversations")
@PreAuthorize("isAuthenticated()")
public class ChatController {
    private final ChatService chatService;

    @GetMapping
    public List<ChatConversationResponse> list(Authentication authentication) {
        return chatService.listConversations(authentication);
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatConversationResponse createDirect(
            @Valid @RequestBody CreateDirectChatRequest request,
            Authentication authentication
    ) {
        return chatService.createDirect(request.userId(), authentication);
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatConversationResponse createGroup(
            @Valid @RequestBody CreateGroupChatRequest request,
            Authentication authentication
    ) {
        return chatService.createGroup(request, authentication);
    }

    @GetMapping("/{conversationId}/messages")
    public ChatMessagePageResponse messages(
            @PathVariable @Size(max = 36) String conversationId,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            Authentication authentication
    ) {
        return chatService.listMessages(conversationId, beforeSeq, afterSeq, limit, authentication);
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendMessage(
            @PathVariable @Size(max = 36) String conversationId,
            @Valid @RequestBody ChatSendMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendMessage(conversationId, request, authentication);
    }

    @PatchMapping("/{conversationId}/read")
    public ChatReadResponse markRead(
            @PathVariable @Size(max = 36) String conversationId,
            @Valid @RequestBody ChatReadRequest request,
            Authentication authentication
    ) {
        return chatService.markRead(conversationId, request, authentication);
    }

    @PatchMapping("/{conversationId}")
    public ChatConversationResponse renameGroup(
            @PathVariable @Size(max = 36) String conversationId,
            @Valid @RequestBody RenameChatRequest request,
            Authentication authentication
    ) {
        return chatService.renameGroup(conversationId, request, authentication);
    }

    @PostMapping("/{conversationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMemberResponse addMember(
            @PathVariable @Size(max = 36) String conversationId,
            @Valid @RequestBody AddChatMemberRequest request,
            Authentication authentication
    ) {
        return chatService.addMember(conversationId, request, authentication);
    }

    @DeleteMapping("/{conversationId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable @Size(max = 36) String conversationId,
            @PathVariable @Size(max = 36) String userId,
            Authentication authentication
    ) {
        chatService.removeMember(conversationId, userId, authentication);
    }

    @PatchMapping("/{conversationId}/members/{userId}/role")
    public ChatMemberResponse changeMemberRole(
            @PathVariable @Size(max = 36) String conversationId,
            @PathVariable @Size(max = 36) String userId,
            @Valid @RequestBody ChangeChatMemberRoleRequest request,
            Authentication authentication
    ) {
        return chatService.changeMemberRole(conversationId, userId, request, authentication);
    }

    @PatchMapping("/{conversationId}/messages/{messageId}")
    public ChatMessageResponse editMessage(
            @PathVariable @Size(max = 36) String conversationId,
            @PathVariable Long messageId,
            @Valid @RequestBody EditChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.editMessage(conversationId, messageId, request, authentication);
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable @Size(max = 36) String conversationId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        chatService.deleteMessage(conversationId, messageId, authentication);
    }
}
