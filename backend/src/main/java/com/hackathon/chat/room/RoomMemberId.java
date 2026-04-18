package com.hackathon.chat.room;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RoomMemberId implements Serializable {

    private UUID roomId;
    private UUID userId;

    public RoomMemberId() {
    }

    public RoomMemberId(UUID roomId, UUID userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomMemberId other)) return false;
        return Objects.equals(roomId, other.roomId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
