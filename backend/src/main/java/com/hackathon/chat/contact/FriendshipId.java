package com.hackathon.chat.contact;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FriendshipId implements Serializable {

    private UUID userAId;
    private UUID userBId;

    public FriendshipId() {
    }

    public FriendshipId(UUID userAId, UUID userBId) {
        this.userAId = userAId;
        this.userBId = userBId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FriendshipId other)) return false;
        return Objects.equals(userAId, other.userAId) && Objects.equals(userBId, other.userBId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userAId, userBId);
    }
}
