package com.smartlab.controller;

import com.smartlab.dto.request.ChatReadRequest;
import com.smartlab.dto.request.ChatSendMessageRequest;
import com.smartlab.dto.request.ChatTypingRequest;
import com.smartlab.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatService chatService;

    @MessageMapping("/chat/{conversationId}/messages")
    public void sendMessage(
            @DestinationVariable String conversationId,
            @Valid @Payload ChatSendMessageRequest request,
            Principal principal
    ) {
        chatService.sendMessage(conversationId, request, authentication(principal));
    }

    @MessageMapping("/chat/{conversationId}/read")
    public void markRead(
            @DestinationVariable String conversationId,
            @Valid @Payload ChatReadRequest request,
            Principal principal
    ) {
        chatService.markRead(conversationId, request, authentication(principal));
    }

    @MessageMapping("/chat/{conversationId}/typing")
    public void typing(
            @DestinationVariable String conversationId,
            @Valid @Payload ChatTypingRequest request,
            Principal principal
    ) {
        chatService.publishTyping(conversationId, request, authentication(principal));
    }

    private org.springframework.security.core.Authentication authentication(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication) {
            return authentication;
        }
        throw new org.springframework.security.authentication.BadCredentialsException(
                "Authenticated STOMP CONNECT is required");
    }
}
