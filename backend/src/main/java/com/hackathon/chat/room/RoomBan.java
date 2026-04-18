package com.hackathon.chat.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_ban")
@IdClass(RoomBanId.class)
public class RoomBan {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "banned_by")
    private UUID bannedBy;

    @Column(length = 200)
    private String reason;

    @Column(name = "banned_at", nullable = false, updatable = false)
    private Instant bannedAt;

    protected RoomBan() {
    }

    public RoomBan(UUID roomId, UUID userId, UUID bannedBy, String reason) {
        this.roomId = roomId;
        this.userId = userId;
        this.bannedBy = bannedBy;
        this.reason = reason;
        this.bannedAt = Instant.now();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBannedBy() {
        return bannedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getBannedAt() {
        return bannedAt;
    }
}
