package com.hackathon.chat.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_invitation")
public class RoomInvitation {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_REVOKED = "revoked";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "invitee_user_id", nullable = false)
    private UUID inviteeUserId;

    @Column(name = "inviter_user_id")
    private UUID inviterUserId;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected RoomInvitation() {
    }

    public RoomInvitation(UUID roomId, UUID inviteeUserId, UUID inviterUserId, String message) {
        this.roomId = roomId;
        this.inviteeUserId = inviteeUserId;
        this.inviterUserId = inviterUserId;
        this.message = message;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getInviteeUserId() {
        return inviteeUserId;
    }

    public UUID getInviterUserId() {
        return inviterUserId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void markAccepted() {
        resolve(STATUS_ACCEPTED);
    }

    public void markDeclined() {
        resolve(STATUS_DECLINED);
    }

    public void markRevoked() {
        resolve(STATUS_REVOKED);
    }

    private void resolve(String newStatus) {
        this.status = newStatus;
        this.resolvedAt = Instant.now();
    }
}
