package com.hackathon.chat.contact;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserBanId implements Serializable {

    private UUID ownerId;
    private UUID targetId;

    public UserBanId() {
    }

    public UserBanId(UUID ownerId, UUID targetId) {
        this.ownerId = ownerId;
        this.targetId = targetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserBanId other)) return false;
        return Objects.equals(ownerId, other.ownerId) && Objects.equals(targetId, other.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, targetId);
    }
}
