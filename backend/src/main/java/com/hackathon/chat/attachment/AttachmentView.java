package com.hackathon.chat.attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentView(
        UUID id,
        UUID messageId,
        UUID conversationId,
        UUID uploaderId,
        String uploaderUsername,
        String originalName,
        String mimeType,
        long sizeBytes,
        String comment,
        boolean isImage,
        Instant createdAt) {
}
