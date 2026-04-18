package com.hackathon.chat.dialog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dialog")
public class Dialog {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Dialog() {
    }

    public Dialog(UUID conversationId, UUID userAId, UUID userBId) {
        int cmp = com.hackathon.chat.contact.OrderedPair.unsignedCompare(userAId, userBId);
        if (cmp == 0) {
            throw new IllegalArgumentException("user_a_id must differ from user_b_id");
        }
        this.conversationId = conversationId;
        this.userAId = cmp < 0 ? userAId : userBId;
        this.userBId = cmp < 0 ? userBId : userAId;
        this.createdAt = Instant.now();
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserAId() {
        return userAId;
    }

    public UUID getUserBId() {
        return userBId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID otherUser(UUID self) {
        if (self.equals(userAId)) return userBId;
        if (self.equals(userBId)) return userAId;
        throw new IllegalArgumentException("User is not a participant");
    }

    public boolean hasParticipant(UUID userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }
}
