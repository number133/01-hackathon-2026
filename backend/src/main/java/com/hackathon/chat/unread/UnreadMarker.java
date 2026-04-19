package com.hackathon.chat.unread;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unread_marker")
@IdClass(UnreadMarkerId.class)
public class UnreadMarker {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UnreadMarker() {
    }

    public UnreadMarker(UUID userId, UUID conversationId, long lastReadSeq) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.lastReadSeq = lastReadSeq;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public UUID getConversationId() { return conversationId; }
    public long getLastReadSeq() { return lastReadSeq; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLastReadSeq(long seq) {
        this.lastReadSeq = seq;
        this.updatedAt = Instant.now();
    }
}
