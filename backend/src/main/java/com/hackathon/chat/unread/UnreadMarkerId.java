package com.hackathon.chat.unread;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UnreadMarkerId implements Serializable {

    private UUID userId;
    private UUID conversationId;

    public UnreadMarkerId() {
    }

    public UnreadMarkerId(UUID userId, UUID conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnreadMarkerId other)) return false;
        return Objects.equals(userId, other.userId)
                && Objects.equals(conversationId, other.conversationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, conversationId);
    }
}
