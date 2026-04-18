package com.hackathon.chat.message;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID id,
        UUID conversationId,
        UUID roomId,
        long seq,
        UUID authorId,
        String authorUsername,
        String body,
        ReplyRef replyTo,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt) {

    public record ReplyRef(
            UUID id,
            long seq,
            String authorUsername,
            String bodyPreview) {
    }
}
