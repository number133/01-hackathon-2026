package com.hackathon.chat.message;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SendMessageRequest(
        @Size(max = 3072) String text,
        UUID replyToId,
        List<UUID> attachmentIds) {

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public boolean hasAttachments() {
        return attachmentIds != null && !attachmentIds.isEmpty();
    }
}
