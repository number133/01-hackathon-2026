package com.hackathon.chat.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_member")
@IdClass(RoomMemberId.class)
public class RoomMember {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected RoomMember() {
    }

    public RoomMember(UUID roomId, UUID userId, String role) {
        this.roomId = roomId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }

    public boolean isAdminOrOwner() {
        return ROLE_OWNER.equals(role) || ROLE_ADMIN.equals(role);
    }
}
