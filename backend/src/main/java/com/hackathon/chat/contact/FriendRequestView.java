package com.hackathon.chat.contact;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestView(
        UUID id,
        UserRef requester,
        UserRef recipient,
        String message,
        FriendRequestStatus status,
        Instant createdAt,
        Instant resolvedAt) {

    public record UserRef(UUID id, String username) {
    }
}
