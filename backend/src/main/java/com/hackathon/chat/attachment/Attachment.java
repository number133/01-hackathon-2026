package com.hackathon.chat.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(UUID conversationId,
                      UUID uploaderId,
                      String storedPath,
                      String originalName,
                      String mimeType,
                      long sizeBytes,
                      String comment) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.uploaderId = uploaderId;
        this.storedPath = storedPath;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getUploaderId() { return uploaderId; }
    public String getStoredPath() { return storedPath; }
    public String getOriginalName() { return originalName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }

    public void linkTo(UUID messageId) {
        this.messageId = messageId;
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
