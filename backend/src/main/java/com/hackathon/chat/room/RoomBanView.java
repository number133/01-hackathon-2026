package com.hackathon.chat.room;

import java.time.Instant;
import java.util.UUID;

public record RoomBanView(
        UUID userId,
        String username,
        UUID bannedById,
        String bannedByUsername,
        String reason,
        Instant bannedAt) {
}
