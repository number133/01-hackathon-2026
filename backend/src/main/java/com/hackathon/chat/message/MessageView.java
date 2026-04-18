package com.hackathon.chat.message;

import com.hackathon.chat.attachment.AttachmentRef;
import java.time.Instant;
import java.util.List;
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
        List<AttachmentRef> attachments,
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
