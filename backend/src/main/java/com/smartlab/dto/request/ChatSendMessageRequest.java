package com.smartlab.dto.request;

import com.smartlab.enums.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatSendMessageRequest(
        @NotBlank(message = "Client message id is required")
        @Size(max = 64, message = "Client message id must not exceed 64 characters")
        String clientMessageId,
        @NotNull(message = "Message type is required")
        ChatMessageType messageType,
        @Size(max = 20_000, message = "Message content must not exceed 20000 characters")
        String content,
        @Positive(message = "Reply message id must be positive")
        Long replyToMessageId,
        @Size(max = 20, message = "A message cannot have more than 20 attachments")
        List<@NotNull @Positive Long> fileIds,
        @Size(max = 20_000, message = "Message metadata must not exceed 20000 characters")
        String metadataJson
) {
    public ChatSendMessageRequest(
            String clientMessageId,
            ChatMessageType messageType,
            String content,
            Long replyToMessageId,
            List<Long> fileIds
    ) {
        this(clientMessageId, messageType, content, replyToMessageId, fileIds, null);
    }
}
