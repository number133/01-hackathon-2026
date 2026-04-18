package com.hackathon.chat.room;

import java.time.Instant;
import java.util.UUID;

public record RoomMemberView(
        UUID userId,
        String username,
        String role,
        Instant joinedAt) {
}
