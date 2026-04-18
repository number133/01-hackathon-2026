package com.hackathon.chat.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friend_request")
public class FriendRequest {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected FriendRequest() {
    }

    public FriendRequest(UUID requesterId, UUID recipientId, String message) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.recipientId = recipientId;
        this.message = message;
        this.status = FriendRequestStatus.PENDING.dbValue();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getMessage() {
        return message;
    }

    public FriendRequestStatus getStatus() {
        return FriendRequestStatus.ofDb(status);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void resolve(FriendRequestStatus newStatus) {
        this.status = newStatus.dbValue();
        this.resolvedAt = Instant.now();
    }
}
