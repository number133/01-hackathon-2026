package com.hackathon.chat.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_ban")
@IdClass(UserBanId.class)
public class UserBan {

    @Id
    @Column(name = "owner_id")
    private UUID ownerId;

    @Id
    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserBan() {
    }

    public UserBan(UUID ownerId, UUID targetId) {
        this.ownerId = ownerId;
        this.targetId = targetId;
        this.createdAt = Instant.now();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
