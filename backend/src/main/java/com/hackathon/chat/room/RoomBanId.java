package com.hackathon.chat.room;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RoomBanId implements Serializable {

    private UUID roomId;
    private UUID userId;

    public RoomBanId() {
    }

    public RoomBanId(UUID roomId, UUID userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomBanId other)) return false;
        return Objects.equals(roomId, other.roomId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, userId);
    }
}
