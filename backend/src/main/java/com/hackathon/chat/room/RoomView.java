package com.hackathon.chat.room;

import java.time.Instant;
import java.util.UUID;

public record RoomView(
        UUID id,
        String name,
        String description,
        String visibility,
        UUID ownerId,
        String ownerUsername,
        long memberCount,
        String myRole,
        Instant createdAt) {
}
